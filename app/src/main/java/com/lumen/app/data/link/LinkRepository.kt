package com.lumen.app.data.link

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lumen.app.data.db.PeerDayDao
import com.lumen.app.data.db.PeerDayEntity
import com.lumen.app.data.prefs.SettingsRepository
import com.lumen.app.data.usage.UsageRepository
import com.lumen.app.domain.DayStats
import com.lumen.app.domain.TimeUtils
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyPair
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.linkStore by preferencesDataStore(name = "lumen_link")

data class NearbyChild(
    val host: String,
    val port: Int,
    val nickname: String,
)

class LinkRepository(
    private val context: Context,
    private val usageRepository: UsageRepository,
    private val settingsRepository: SettingsRepository,
    private val peerDayDao: PeerDayDao,
) {
    private object Keys {
        val NICKNAME = stringPreferencesKey("nickname")
        val IDENTITY_ALG = stringPreferencesKey("identity_alg")
        val IDENTITY_PRIV = stringPreferencesKey("identity_priv")
        val IDENTITY_PUB = stringPreferencesKey("identity_pub")
        val PEER_LIST = stringPreferencesKey("peer_list")
        val LINK_ENABLED = booleanPreferencesKey("link_enabled")
        val ALERTED_LIMITS = stringSetPreferencesKey("alerted_limits")
        val CELEBRATED_WEEK = longPreferencesKey("celebrated_week")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _linkEnabled = MutableStateFlow(false)
    val linkEnabled: StateFlow<Boolean> = _linkEnabled.asStateFlow()

    private val _peers = MutableStateFlow<List<PeerInfo>>(emptyList())
    val peers: StateFlow<List<PeerInfo>> = _peers.asStateFlow()

    private val _snapshots = MutableStateFlow<Map<String, ChildSnapshot>>(emptyMap())
    val snapshots: StateFlow<Map<String, ChildSnapshot>> = _snapshots.asStateFlow()

    private val _histories = MutableStateFlow<Map<String, List<DayStats>>>(emptyMap())
    val histories: StateFlow<Map<String, List<DayStats>>> = _histories.asStateFlow()

    private val _nearby = MutableStateFlow<List<NearbyChild>>(emptyList())
    val nearby: StateFlow<List<NearbyChild>> = _nearby.asStateFlow()

    private val _incomingKnock = MutableStateFlow<Pair<String, String>?>(null)
    val incomingKnock: StateFlow<Pair<String, String>?> = _incomingKnock.asStateFlow()

    private val _celebratedWeek = MutableStateFlow<Long>(0L)
    val celebratedWeek: StateFlow<Long> = _celebratedWeek.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var nsdManager: NsdManager? = null
    private var registeredService: NsdServiceInfo? = null
    private var browseJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private suspend fun generateAndSaveIdentity(): KeyPair {
        val keyPair = LumenCrypto.generateKeyPair()
        val algorithm = if (keyPair.public.algorithm == "X25519") "X25519" else "EC"
        context.linkStore.edit {
            it[Keys.IDENTITY_ALG] = algorithm
            it[Keys.IDENTITY_PRIV] = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)
            it[Keys.IDENTITY_PUB] = LumenCrypto.encodePublic(keyPair.public)
        }
        return keyPair
    }

    suspend fun ensureIdentity(): KeyPair {
        val prefs = context.linkStore.data.first()
        val alg = prefs[Keys.IDENTITY_ALG] ?: ""
        val privB64 = prefs[Keys.IDENTITY_PRIV] ?: ""
        val pubB64 = prefs[Keys.IDENTITY_PUB] ?: ""
        if (alg.isNotEmpty() && privB64.isNotEmpty() && pubB64.isNotEmpty()) {
            return try {
                val priv = KeyPersistence.privateKey(alg, privB64)
                val pub = LumenCrypto.decodePublic(alg, pubB64)
                KeyPair(pub, priv)
            } catch (_: Exception) {
                generateAndSaveIdentity()
            }
        }
        return generateAndSaveIdentity()
    }

    suspend fun nickname(): String =
        context.linkStore.data.first()[Keys.NICKNAME] ?: "My phone"

    suspend fun setNickname(name: String) {
        context.linkStore.edit { it[Keys.NICKNAME] = name.trim().take(24).ifEmpty { "My phone" } }
    }

    suspend fun pairingPayload(): String {
        val identity = ensureIdentity()
        return Pairing.buildPayload(identity, nickname())
    }

    suspend fun pairWith(payload: String): Pair<Boolean, String> {
        val result = Pairing.parse(payload)
        val peer = result?.peer
        if (peer == null) {
            val reason = result?.problem
                ?: "That doesn't look like a Lumen link code. Check that the full code starts with " +
                    "lumenlink://pair?p= and try again."
            return false to reason
        }
        val identity = ensureIdentity()
        if (LumenCrypto.constantTimeEquals(
                LumenCrypto.publicKeyBytes(identity.public),
                LumenCrypto.publicKeyBytes(LumenCrypto.decodePublic(peer.algorithm, peer.publicKeyB64)),
            )
        ) {
            return false to "That's this phone's own link code. The other phone has to scan it — " +
                "or paste a code copied from the other phone."
        }
        val current = context.linkStore.data.first()[Keys.PEER_LIST] ?: "[]"
        val peers = parsePeers(current).filter { it.id != peer.id } + peer
        context.linkStore.edit {
            it[Keys.PEER_LIST] = encodePeers(peers)
        }
        _peers.value = peers
        return true to peer.nickname
    }

    suspend fun removePeer(peerId: String) {
        val remaining = _peers.value.filter { it.id != peerId }
        context.linkStore.edit { it[Keys.PEER_LIST] = encodePeers(remaining) }
        _peers.value = remaining
        _snapshots.value = _snapshots.value - peerId
        _histories.value = _histories.value - peerId
        peerDayDao.deleteForPeer(peerId)
    }

    suspend fun snapshotFor(peerId: String): ChildSnapshot? = _snapshots.value[peerId]

    suspend fun loadInitial() {
        val prefs = context.linkStore.data.first()
        _linkEnabled.value = prefs[Keys.LINK_ENABLED] ?: false
        _peers.value = parsePeers(prefs[Keys.PEER_LIST] ?: "[]")
        if (_peers.value.isNotEmpty()) {
            _histories.value = _peers.value.associate { it.id to loadHistory(it.id) }
        }
        if (_linkEnabled.value) startServer()
    }

    private suspend fun persistSnapshot(peerId: String, snapshot: ChildSnapshot) {        val now = System.currentTimeMillis()
        val rows = (snapshot.week + listOfNotNull(snapshot.today))
            .distinctBy { it.date }
            .map { day ->
                PeerDayEntity(
                    peerId = peerId,
                    dateEpochDay = day.date.toEpochDay(),
                    screenTimeMs = day.screenTimeMs,
                    unlocks = day.unlocks,
                    notificationCount = day.notificationCount,
                    focusMinutes = day.focusMinutes,
                    syncedAtMs = now,
                )
            }
        peerDayDao.upsertAll(rows)
        peerDayDao.deleteOlderThanDay(TimeUtils.today().minusDays(60).toEpochDay())
        _histories.value = _histories.value + (peerId to loadHistory(peerId))
    }

    private suspend fun loadHistory(peerId: String): List<DayStats> {
        val today = TimeUtils.today()
        return peerDayDao.getBetween(peerId, today.minusDays(29).toEpochDay(), today.toEpochDay())
            .map { row ->
                DayStats(
                    date = LocalDate.ofEpochDay(row.dateEpochDay),
                    screenTimeMs = row.screenTimeMs,
                    unlocks = row.unlocks,
                    notificationCount = row.notificationCount,
                    focusMinutes = row.focusMinutes,
                    apps = emptyList(),
                )
            }
    }

    private suspend fun checkCrossedLimits(peerId: String, snapshot: ChildSnapshot) {
        val today = snapshot.today ?: return
        val limits = snapshot.limits
        if (limits.isEmpty()) return
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val epochDay = today.date.toEpochDay()
        val alerted = context.linkStore.data.first()[Keys.ALERTED_LIMITS] ?: emptySet()
        val keep = alerted.filterTo(mutableSetOf()) { entry ->
            entry.substringAfter('|', "").substringBefore('|', "").toLongOrNull()
                ?.let { it >= TimeUtils.today().toEpochDay() } == true
        }
        var changed = false
        for (app in today.apps) {
            val limitMinutes = limits[app.packageName] ?: continue
            if (app.timeMs < limitMinutes * 60_000L) continue
            val key = "$peerId|$epochDay|${app.packageName}"
            if (key in keep) continue
            keep += key
            changed = true
            notifyCrossedLimit(snapshot.nickname, app, limitMinutes)
        }
        if (changed) {
            context.linkStore.edit { it[Keys.ALERTED_LIMITS] = keep }
        }
    }

    private fun notifyCrossedLimit(nickname: String, app: com.lumen.app.domain.AppUsage, limitMinutes: Int) {
        val manager = androidx.core.app.NotificationManagerCompat.from(context)
        val notification = androidx.core.app.NotificationCompat.Builder(context, "lumen_watch")
            .setSmallIcon(com.lumen.app.R.drawable.ic_launcher_foreground)
            .setContentTitle("Limit crossed on ${nickname}'s phone")
            .setContentText(
                "${app.label}: ${TimeUtils.formatDuration(app.timeMs)} today — " +
                    "their daily limit is ${limitMinutes} min.",
            )
            .setAutoCancel(true)
            .build()
        val id = 3000 + kotlin.math.abs(app.packageName.hashCode() % 1000)
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    fun setLinkEnabled(enabled: Boolean) {
        if (enabled == _linkEnabled.value) return
        scope.launch {
            context.linkStore.edit { it[Keys.LINK_ENABLED] = enabled }
        }
        if (enabled) startServer() else stopServer()
        _linkEnabled.value = enabled
    }

    private fun startServer() {
        stopServer()
        serverJob = scope.launch {
            val identity = ensureIdentity()
            val server = try {
                ServerSocket(8787)
            } catch (_: Exception) {
                ServerSocket(0)
            }
            server.reuseAddress = true
            server.soTimeout = 0
            serverSocket = server
            val name = "LumenLink-${nickname()}"
            registerNsd(name, server.localPort)
            while (serverJob?.isActive == true) {
                try {
                    val socket = server.accept()
                    scope.launch { handleChildConnection(socket, identity) }
                } catch (_: Exception) {
                    break
                }
            }
        }
    }

    private fun stopServer() {
        UnregisterNsd()
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
    }

    private suspend fun handleChildConnection(socket: Socket, identity: KeyPair) {
        try {
            socket.soTimeout = 20_000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8), 8192)
            val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)

            val clientLine = readLine(reader) ?: return
            val clientPubB64 = LinkProtocol.parseHello(clientLine, "C") ?: return
            val clientPublic = try {
                LumenCrypto.decodePublic(identity.public.algorithm.takeIf { it == "X25519" } ?: "EC", clientPubB64)
            } catch (_: Exception) {
                return
            }
            val serverPubB64 = LumenCrypto.encodePublic(identity.public)
            writer.write(LinkProtocol.serverHello(serverPubB64))
            writer.flush()

            val shared = LumenCrypto.deriveShared(identity.private, clientPublic)
            val expected = LumenCrypto.mac(
                shared,
                LinkProtocol.authData('c', clientPubB64, serverPubB64),
            )
            val authLine = readLine(reader) ?: return
            val clientMac = Base64.decode(
                LinkProtocol.parseHello(authLine, "A") ?: return,
                Base64.NO_WRAP,
            )
            if (!LumenCrypto.constantTimeEquals(clientMac, expected)) {
                socket.close()
                return
            }

            val serverMac = LumenCrypto.mac(
                shared,
                LinkProtocol.authData('s', serverPubB64, clientPubB64),
            )
            writer.write(
                LinkProtocol.serverAuth(
                    Base64.encodeToString(serverMac, Base64.NO_WRAP),
                ),
            )
            writer.flush()

            val sessionKey = LumenCrypto.hkdf(shared, LinkProtocol.sessionInfo())
            val settings = settingsRepository.settings.first()
            val nickname = nickname()
            val hasAccess = usageRepository.hasUsageAccess()
            val today: DayStats?
            val week: List<DayStats>
            if (hasAccess) {
                val start = if (settings.installedEpochDay > 0) {
                    java.time.LocalDate.ofEpochDay(settings.installedEpochDay)
                } else {
                    TimeUtils.today().minusDays(14)
                }
                val history = usageRepository.history(
                    minOf(start, TimeUtils.today().minusDays(6)),
                    settings.excludedPackages,
                    settings.notificationTracking,
                )
                today = history.lastOrNull()
                week = history.takeLast(7)
            } else {
                today = null
                week = emptyList()
            }
            val bundle = LinkProtocol.buildSnapshot(
                nickname,
                hasAccess,
                today,
                week,
                settings.appLimitsMinutes,
                settings.dailyGoalFor(TimeUtils.today()),
            )
            val sealed = LumenCrypto.seal(sessionKey, bundle)
            writer.write(LinkProtocol.report(Base64.encodeToString(sealed, Base64.NO_WRAP)))
            writer.flush()
            socket.soTimeout = 5_000
            val knockLine = readLine(reader)
            if (knockLine != null) {
                val sealedB64 = LinkProtocol.parseHello(knockLine, "K")
                if (sealedB64 != null) {
                    try {
                        val plain = LumenCrypto.open(
                            sessionKey,
                            Base64.decode(sealedB64, Base64.NO_WRAP),
                        )
                        val knock = LinkProtocol.parseKnock(plain)
                        if (knock != null) deliverKnock(knock.first, knock.second)
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun syncWith(
        peer: PeerInfo,
        host: String,
        port: Int,
        knockText: String? = null,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val identity = ensureIdentity()
                val shared = LumenCrypto.deriveShared(
                    identity.private,
                    LumenCrypto.decodePublic(peer.algorithm, peer.publicKeyB64),
                )
                val sessionKey = LumenCrypto.hkdf(shared, LinkProtocol.sessionInfo())
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 15_000)
                    socket.soTimeout = 20_000
                    val reader = BufferedReader(
                        InputStreamReader(socket.getInputStream(), Charsets.UTF_8),
                        8192,
                    )
                    val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)

                    val clientPubB64 = LumenCrypto.encodePublic(identity.public)
                    writer.write(LinkProtocol.clientHello(clientPubB64))
                    writer.flush()

                    val greetLine = readLine(reader) ?: throw IllegalStateException("Child did not respond.")
                    val serverPubB64 = LinkProtocol.parseHello(greetLine, "S")
                        ?: throw IllegalStateException("Child did not respond.")
                    val peerPublic = LumenCrypto.decodePublic(peer.algorithm, peer.publicKeyB64)
                    val algorithm = if (peerPublic.algorithm == "X25519") "X25519" else "EC"
                    val serverCheck = LumenCrypto.decodePublic(algorithm, serverPubB64)
                    val serverRaw = LumenCrypto.publicKeyBytes(serverCheck)
                    val peerRaw = LumenCrypto.publicKeyBytes(peerPublic)
                    if (!LumenCrypto.constantTimeEquals(serverRaw, peerRaw)) {
                        throw IllegalStateException("Wrong phone — pairing key mismatch.")
                    }

                    val clientMac = LumenCrypto.mac(
                        shared,
                        LinkProtocol.authData('c', clientPubB64, serverPubB64),
                    )
                    writer.write(
                        LinkProtocol.clientAuth(
                            Base64.encodeToString(clientMac, Base64.NO_WRAP),
                        ),
                    )
                    writer.flush()

                    val authReply = readLine(reader) ?: throw IllegalStateException("Child refused the link.")
                    val serverMacB64 = LinkProtocol.parseHello(authReply, "B")
                        ?: throw IllegalStateException("Child refused the link.")
                    val expectedServer = LumenCrypto.mac(
                        shared,
                        LinkProtocol.authData('s', serverPubB64, clientPubB64),
                    )
                    val gotServer = Base64.decode(serverMacB64, Base64.NO_WRAP)
                    if (!LumenCrypto.constantTimeEquals(gotServer, expectedServer)) {
                        throw IllegalStateException("Security check failed.")
                    }

                    val reportLine = readLine(reader) ?: throw IllegalStateException("Child sent no report.")
                    val sealed = Base64.decode(
                        LinkProtocol.parseHello(reportLine, "R")
                            ?: throw IllegalStateException("Child sent no report."),
                        Base64.NO_WRAP,
                    )
                    val plain = LumenCrypto.open(sessionKey, sealed)
                    val snapshot = LinkProtocol.parseSnapshot(plain)
                        ?: throw IllegalStateException("Could not read the report.")

                    _snapshots.value = _snapshots.value + (peer.id to snapshot)
                    persistSnapshot(peer.id, snapshot)
                    checkCrossedLimits(peer.id, snapshot)
                    rememberHost(peer.id, host)
                    if (!knockText.isNullOrBlank()) {
                        val payload = LinkProtocol.knockPayload(
                            nickname().take(24),
                            knockText.take(140),
                        )
                        val sealedKnock = LumenCrypto.seal(sessionKey, payload)
                        writer.write(
                            LinkProtocol.knock(
                                Base64.encodeToString(sealedKnock, Base64.NO_WRAP),
                            ),
                        )
                        writer.flush()
                    }
                    "Synced ${formatAge(snapshot.syncedAtMs)}"
                }
            }
        }

    private fun deliverKnock(from: String, text: String) {
        _incomingKnock.value = Pair(from, text)
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = androidx.core.app.NotificationCompat.Builder(context, "lumen_knock")
            .setSmallIcon(com.lumen.app.R.drawable.ic_launcher_foreground)
            .setContentTitle("Knock from $from")
            .setContentText(text)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        androidx.core.app.NotificationManagerCompat.from(context).notify(5000, notification)
    }

    suspend fun celebrateMutualWeek(peerNickname: String) {
        val weekKey = TimeUtils.today().toEpochDay() / 7
        val prefs = context.linkStore.data.first()
        if (prefs[Keys.CELEBRATED_WEEK] == weekKey) return
        context.linkStore.edit { it[Keys.CELEBRATED_WEEK] = weekKey }
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = androidx.core.app.NotificationCompat.Builder(context, "lumen_celebration")
            .setSmallIcon(com.lumen.app.R.drawable.ic_launcher_foreground)
            .setContentTitle("Sync celebration")
            .setContentText(
                "You and $peerNickname both stayed under your goals this week. That's a shared win.",
            )
            .setAutoCancel(true)
            .build()
        androidx.core.app.NotificationManagerCompat.from(context).notify(6000, notification)
    }

    fun startDiscovery() {
        if (browseJob != null) return
        browseJob = scope.launch {
            acquireMulticastLock()
            try {
                val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
                val listener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) {}

                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        manager.resolveService(
                            serviceInfo,
                            object : NsdManager.ResolveListener {
                                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}

                                override fun onServiceResolved(info: NsdServiceInfo) {
                                    val nickname = info.serviceName.removePrefix("LumenLink-")
                                    val host = info.host?.hostAddress ?: return
                                    scope.launch {
                                        _nearby.value = (_nearby.value.filterNot {
                                            it.host == host && it.port == info.port
                                        }) + NearbyChild(
                                            host = host,
                                            port = info.port,
                                            nickname = nickname,
                                        )
                                    }
                                }
                            },
                        )
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                        scope.launch {
                            _nearby.value = _nearby.value.filterNot {
                                it.nickname == serviceInfo.serviceName.removePrefix("LumenLink-")
                            }
                        }
                    }

                    override fun onDiscoveryStopped(serviceType: String) {}

                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}

                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                }
                manager.discoverServices(LINK_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
                while (browseJob?.isActive == true) {
                    kotlinx.coroutines.delay(2_000)
                }
                try {
                    manager.stopServiceDiscovery(listener)
                } catch (_: Exception) {
                }
            } catch (_: Exception) {
            } finally {
                releaseMulticastLock()
            }
        }
    }

    fun stopDiscovery() {
        browseJob?.cancel()
        browseJob = null
        _nearby.value = emptyList()
    }

    private suspend fun registerNsd(name: String, port: Int) {
        try {
            val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            nsdManager = manager
            acquireMulticastLock()
            val info = NsdServiceInfo().apply {
                serviceName = name
                serviceType = LINK_SERVICE_TYPE
                setPort(port)
            }
            manager.registerService(
                info,
                NsdManager.PROTOCOL_DNS_SD,
                object : NsdManager.RegistrationListener {
                    override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                        registeredService = serviceInfo
                    }

                    override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
                    override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                },
            )
            registeredService = info
        } catch (_: Exception) {
        }
    }

    private fun UnregisterNsd() {
        val registered = registeredService ?: return
        try {
            nsdManager?.unregisterService(object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            })
        } catch (_: Exception) {
        }
        registeredService = null
        releaseMulticastLock()
    }

    private fun acquireMulticastLock() {
        try {
            val wifi = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val lock = wifi.createMulticastLock("lumenlink")
            lock.setReferenceCounted(false)
            lock.acquire()
            multicastLock = lock
        } catch (_: Exception) {
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
        multicastLock = null
    }

    private suspend fun readLine(reader: BufferedReader): String? {
        val line = reader.readLine() ?: return null
        if (line.length > 128_000) return null
        return line
    }

    private fun parsePeers(json: String): List<PeerInfo> {
        val arr = org.json.JSONArray(json)
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.getJSONObject(i)
            try {
                PeerInfo(
                    id = obj.getString("id"),
                    nickname = obj.getString("n"),
                    algorithm = obj.getString("a"),
                    publicKeyB64 = obj.getString("k"),
                    pairedAtMs = obj.getLong("t"),
                    host = obj.optString("h", "").ifEmpty { null },
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun encodePeers(peers: List<PeerInfo>): String {
        val arr = org.json.JSONArray()
        peers.forEach { peer ->
            arr.put(
                org.json.JSONObject()
                    .put("id", peer.id)
                    .put("n", peer.nickname)
                    .put("a", peer.algorithm)
                    .put("k", peer.publicKeyB64)
                    .put("t", peer.pairedAtMs)
                    .apply {
                        peer.host?.let { put("h", it) }
                    },
            )
        }
        return arr.toString()
    }

    private suspend fun rememberHost(peerId: String, host: String) {
        val updated = _peers.value.map { peer ->
            if (peer.id == peerId) peer.copy(host = host) else peer
        }
        if (updated != _peers.value) {
            _peers.value = updated
            context.linkStore.edit { it[Keys.PEER_LIST] = encodePeers(updated) }
        }
    }

    suspend fun buildExportBytes(pin: String): ByteArray {
        val identity = ensureIdentity()
        val settings = settingsRepository.settings.first()
        val history = usageRepository.history(
            TimeUtils.today().minusDays(29),
            settings.excludedPackages,
            settings.notificationTracking,
        )
        val peer = PeerInfo(
            id = LumenCrypto.shortId(LumenCrypto.publicKeyBytes(identity.public)),
            nickname = nickname(),
            algorithm = if (identity.public.algorithm == "X25519") "X25519" else "EC",
            publicKeyB64 = LumenCrypto.encodePublic(identity.public),
            pairedAtMs = System.currentTimeMillis(),
        )
        return LinkProtocol.buildExport(peer, history, pin)
    }

    suspend fun importArchive(bytes: ByteArray, pin: String): Result<String> = runCatching {
        val bundle = LinkProtocol.parseExport(bytes, pin)
            ?: throw IllegalStateException("Wrong PIN, or this isn't a Lumen report file.")
        val now = System.currentTimeMillis()
        val current = _peers.value
        val known = current.any { it.id == bundle.peer.id }
        if (!known) {
            val updated = current + bundle.peer
            context.linkStore.edit { it[Keys.PEER_LIST] = encodePeers(updated) }
            _peers.value = updated
        }
        val rows = bundle.days.map { day ->
            PeerDayEntity(
                peerId = bundle.peer.id,
                dateEpochDay = day.date.toEpochDay(),
                screenTimeMs = day.screenTimeMs,
                unlocks = day.unlocks,
                notificationCount = day.notificationCount,
                focusMinutes = day.focusMinutes,
                syncedAtMs = now,
            )
        }
        peerDayDao.upsertAll(rows)
        peerDayDao.deleteOlderThanDay(TimeUtils.today().minusDays(60).toEpochDay())
        _histories.value = _histories.value + (bundle.peer.id to loadHistory(bundle.peer.id))
        val last = bundle.days.maxByOrNull { it.date }
        if (last != null) {
            _snapshots.value = _snapshots.value + (bundle.peer.id to ChildSnapshot(
                syncedAtMs = now,
                nickname = bundle.peer.nickname,
                hasAccess = true,
                today = last,
                week = bundle.days.takeLast(7),
                limits = emptyMap(),
            ))
        }
        if (known) {
            "Imported ${bundle.days.size} days of ${bundle.peer.nickname}'s history."
        } else {
            "Linked to ${bundle.peer.nickname} and imported ${bundle.days.size} days."
        }
    }

    suspend fun buildSnapshotNow(): ChildSnapshot {
        val nickname = nickname()
        val hasAccess = usageRepository.hasUsageAccess()
        if (!hasAccess) {
            return ChildSnapshot(System.currentTimeMillis(), nickname, false, null, emptyList())
        }
        val settings = settingsRepository.settings.first()
        val history = usageRepository.history(
            TimeUtils.today().minusDays(6),
            settings.excludedPackages,
            settings.notificationTracking,
        )
        return ChildSnapshot(
            syncedAtMs = System.currentTimeMillis(),
            nickname = nickname,
            hasAccess = true,
            today = history.lastOrNull(),
            week = history,
            limits = settings.appLimitsMinutes,
        )
    }

    companion object {
        const val LINK_SERVICE_TYPE = "_lumenlink._tcp."
    }
}

object KeyPersistence {
    fun privateKey(algorithm: String, b64: String): java.security.PrivateKey {
        val raw = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
        return java.security.KeyFactory.getInstance(algorithm)
            .generatePrivate(java.security.spec.PKCS8EncodedKeySpec(raw))
    }

    fun publicKey(algorithm: String, b64: String): java.security.PublicKey =
        LumenCrypto.decodePublic(algorithm, b64)
}

fun formatAge(ms: Long): String {
    val minutes = (System.currentTimeMillis() - ms) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        else -> {
            val hours = minutes / 60
            if (hours < 24) "${hours}h ago" else "${hours / 24}d ago"
        }
    }
}