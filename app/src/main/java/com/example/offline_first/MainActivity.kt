package com.example.offline_first

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.offline_first.data.local.AppDatabase
import com.example.offline_first.data.model.Report
import com.example.offline_first.data.model.SyncStatus
import com.example.offline_first.databinding.ActivityMainBinding
import com.example.offline_first.sync.ConnectivityObserver
import com.example.offline_first.sync.SyncManager
import com.example.offline_first.ui.screens.addreport.AddReportActivity
import com.example.offline_first.ui.screens.login.LoginActivity
import com.example.offline_first.ui.screens.adapters.ReportAdapter
import com.example.offline_first.util.NetworkUtils
import com.google.firebase.auth.FirebaseAuth
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val auth = FirebaseAuth.getInstance()
    private val reportAdapter = ReportAdapter()
    private val dao by lazy { AppDatabase.getInstance(this).reportDao() }
    private val userDao by lazy { AppDatabase.getInstance(this).userDao() }
    private val syncManager by lazy { SyncManager(dao) }
    private val connectivityObserver by lazy { ConnectivityObserver(this) }
    private var reportFlowJob: Job? = null
    //   syncJob collects that flow. Whenever online flips to true — whether the user turned on WiFi, walked into cell coverage, or just opened the app — syncManager.sync() runs
    //  automatically. The Room Flow (reportFlowJob) then picks up the DB writes and updates the RecyclerView without any user action.
    private var syncJob: Job? = null

    private val currentEmail: String
        get() = auth.currentUser?.email
            ?: getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_LOCAL_AUTH_EMAIL, "") ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setSupportActionBar(binding.toolbar)

        binding.recyclerReports.adapter = reportAdapter

        reportAdapter.onItemClick = { report ->
            startActivity(
                Intent(this, com.example.offline_first.ui.screens.report_details.ReportDetailActivity::class.java)
                    .putExtra(com.example.offline_first.ui.screens.report_details.ReportDetailActivity.EXTRA_REPORT_ID, report.id)
            )
        }

        reportAdapter.onDeleteClick = { report ->
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Remove Report")
                .setMessage("Are you sure you want to remove \"${report.title}\"?")
                .setPositiveButton("Remove") { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            val current = dao.getById(report.id) ?: return@withContext
                            // always go through DELETED_PENDING — even if remoteId is null,
                            // the upload may have partially reached Firestore (localId was used
                            // as the doc ID), so sync will attempt delete using localId as fallback
                            dao.update(current.copy(
                                syncStatus = SyncStatus.DELETED_PENDING,
                                updatedAt = System.currentTimeMillis()
                            ))
                            if (NetworkUtils.isOnline(this@MainActivity)) {
                                syncManager.syncDeletes()
                            }
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        reportAdapter.onRestoreClick = { report ->
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    dao.update(report.copy(
                        syncStatus = SyncStatus.PENDING,
                        updatedAt = System.currentTimeMillis()
                    ))
                }
            }
        }

        binding.fab.setOnClickListener {
            startActivity(Intent(this, AddReportActivity::class.java))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sync -> {
                lifecycleScope.launch {
                    val hasPending = withContext(Dispatchers.IO) {
                        dao.getPending().isNotEmpty() || dao.getDeletedPending().isNotEmpty()
                    }
                    when {
                        !hasPending -> Toast.makeText(this@MainActivity, "Everything is up to date", Toast.LENGTH_SHORT).show()
                        !NetworkUtils.isOnline(this@MainActivity) -> Toast.makeText(this@MainActivity, "No internet connection", Toast.LENGTH_SHORT).show()
                        else -> {
                            Toast.makeText(this@MainActivity, "Syncing…", Toast.LENGTH_SHORT).show()
                            val synced = withContext(Dispatchers.IO) { syncManager.sync() }
                            Toast.makeText(this@MainActivity, "$synced report(s) synced", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                true
            }
            R.id.action_about -> {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.app_name))
                    .setMessage("Version 1.0\n\nReport local issues and sync them to the cloud when online.")
                    .setPositiveButton("OK", null)
                    .show()
                true
            }
            R.id.action_logout -> {
                logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        checkSession()
    }

    private fun checkSession() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val loginTime = prefs.getLong(KEY_LOGIN_TIME, 0L)
        val elapsed = System.currentTimeMillis() - loginTime

        if (loginTime > 0L && elapsed > SESSION_DURATION_MS) {
            auth.signOut()
            prefs.edit().remove(KEY_LOGIN_TIME).remove(KEY_LOCAL_AUTH_EMAIL).apply()
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_LONG).show()
            goToLogin()
            return
        }

        val firebaseUser = auth.currentUser
        if (firebaseUser != null) {
            showHome(firebaseUser.displayName.orEmpty(), firebaseUser.email.orEmpty())
            return
        }

        val localEmail = prefs.getString(KEY_LOCAL_AUTH_EMAIL, null)
        if (localEmail != null && loginTime > 0L) {
            lifecycleScope.launch {
                val localUser = withContext(Dispatchers.IO) { userDao.findByEmail(localEmail) }
                showHome(localUser?.displayName.orEmpty(), localEmail)
            }
            return
        }

        goToLogin()
    }

    private fun showHome(displayName: String, email: String) {
        supportActionBar?.title = displayName.takeIf { it.isNotBlank() } ?: "Welcome back"
        supportActionBar?.subtitle = email

        // cancel any previous observer for a different session
        // cancels the previous collection if showHome is called again (e.g. on onResume), so you never have two collectors running at the same time.
        reportFlowJob?.cancel()
        // starts collecting the flow. Every emission calls submitList, which DiffUtil diffs against the previous list and animates only what changed.
        reportFlowJob = lifecycleScope.launch {
            dao.getByOwnerFlow(email)
                .flowOn(Dispatchers.IO)
                .collect { reports ->
                    val previousCount = reportAdapter.itemCount
                    reportAdapter.submitList(reports) {
                        if (reports.size > previousCount) {
                            binding.recyclerReports.scrollToPosition(0)
                        }
                    }
                    val isEmpty = reports.isEmpty()
                    binding.layoutEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
                    binding.recyclerReports.visibility = if (isEmpty) View.GONE else View.VISIBLE
                }
        }

        // auto-sync whenever network becomes available, even without any user action
        syncJob?.cancel()
        syncJob = lifecycleScope.launch {
            connectivityObserver.isOnline.collect { online ->
                if (online) {
                    withContext(Dispatchers.IO) { syncManager.sync() }
                }
            }
        }
    }

    private fun logout() {
        auth.signOut()
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .remove(KEY_LOGIN_TIME)
            .remove(KEY_LOCAL_AUTH_EMAIL)
            .apply()
        goToLogin()
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun sampleReports(): List<Report> {
        val now = System.currentTimeMillis()
        return listOf(
            Report(
                id = 1, localId = "local-001", remoteId = "remote-001",
                title = "Projector shows no signal",
                description = "Lecture hall projector powers on but reports no signal from any laptop, HDMI or USB-C.",
                category = "Projector/AV",
                location = "Room 3A, Engineering Building",
                photoPath = null,
                owner = auth.currentUser?.email.orEmpty(),
                createdAt = now - 86_400_000, updatedAt = now - 86_400_000,
                syncStatus = SyncStatus.SYNCED
            ),
            Report(
                id = 2, localId = "local-002", remoteId = null,
                title = "Wi-Fi keeps dropping in study area",
                description = "Devices disconnect from the campus network every few minutes near the east windows.",
                category = "Network/Wi-Fi",
                location = "Library, 2nd Floor East",
                photoPath = null,
                owner = auth.currentUser?.email.orEmpty(),
                createdAt = now - 3_600_000, updatedAt = now - 3_600_000,
                syncStatus = SyncStatus.PENDING
            ),
            Report(
                id = 3, localId = "local-003", remoteId = "remote-003",
                title = "Broken seat in lecture hall",
                description = "Seat back has snapped off in the third row; the chair is unsafe to sit on.",
                category = "Furniture",
                location = "Lecture Hall B, Row 3",
                photoPath = null,
                owner = auth.currentUser?.email.orEmpty(),
                createdAt = now - 172_800_000, updatedAt = now - 172_800_000,
                syncStatus = SyncStatus.SYNCED
            ),
            Report(
                id = 4, localId = "local-004", remoteId = "remote-004",
                title = "Air conditioning stuck on full cold",
                description = "The room is unusably cold and the wall panel does not respond to the temperature buttons.",
                category = "Air conditioning",
                location = "Seminar Room 12, Science Block",
                photoPath = null,
                owner = auth.currentUser?.email.orEmpty(),
                createdAt = now - 7_200_000, updatedAt = now - 7_200_000,
                syncStatus = SyncStatus.DELETED_PENDING
            ),
            Report(
                id = 5, localId = "local-005", remoteId = null,
                title = "Fume hood alarm sounding continuously",
                description = "Low-airflow alarm has been sounding since this morning; the bench cannot be used safely.",
                category = "Lab equipment",
                location = "Chemistry Lab 2, Bench 5",
                photoPath = null,
                owner = auth.currentUser?.email.orEmpty(),
                createdAt = now - 1_800_000, updatedAt = now - 1_800_000,
                syncStatus = SyncStatus.PENDING
            )
        )
    }

    companion object {
        const val PREFS_NAME = "session_prefs"
        const val KEY_LOGIN_TIME = "login_time"
        const val KEY_LOCAL_AUTH_EMAIL = "local_auth_email"
        const val SESSION_DURATION_MS = 60 * 60 * 1000L
    }
}