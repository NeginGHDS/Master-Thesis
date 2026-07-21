package com.example.offline_first.ui.screens.report_details

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.offline_first.R
import com.example.offline_first.data.local.AppDatabase
import com.example.offline_first.data.model.Report
import com.example.offline_first.data.model.SyncStatus
import com.example.offline_first.databinding.ActivityReportDetailBinding
import com.example.offline_first.sync.SyncManager
import com.example.offline_first.ui.screens.addreport.AddReportActivity
import com.example.offline_first.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportDetailBinding
    private val dao by lazy { AppDatabase.getInstance(this).reportDao() }
    private val syncManager by lazy { SyncManager(dao) }
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    private var reportId = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityReportDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        supportActionBar?.apply {
            title = getString(R.string.report_detail)
            setDisplayHomeAsUpEnabled(true)
        }

        reportId = intent.getLongExtra(EXTRA_REPORT_ID, -1L)
        if (reportId == -1L) { finish(); return }

        lifecycleScope.launch {
            dao.getByIdFlow(reportId)
                .filterNotNull()
                .flowOn(Dispatchers.IO)
                .collect { report ->
                    bind(report)
                }
        }
    }

    override fun onResume() {
        super.onResume()
        if (reportId == -1L) return
        lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) { dao.getById(reportId) } ?: return@launch
            if (report.syncStatus == SyncStatus.PENDING &&
                NetworkUtils.isOnline(this@ReportDetailActivity)
            ) {
                withContext(Dispatchers.IO) { syncManager.sync() }
            }
        }
    }

    private fun bind(report: Report) {
        binding.textDetailTitle.text = report.title
        binding.textDetailCategory.text = report.category
        binding.textDetailDescription.text = report.description
        binding.textDetailLocation.text = "📍 ${report.location}"
        binding.textDetailDates.text =
            "Created: ${dateFormat.format(Date(report.createdAt))}\n" +
            "Updated: ${dateFormat.format(Date(report.updatedAt))}"

        val (label, colorRes, iconRes) = when (report.syncStatus) {
            SyncStatus.SYNCED          -> Triple("Synced",         R.color.sync_synced,   R.drawable.ic_synced)
            SyncStatus.PENDING         -> Triple("Pending",        R.color.sync_pending,  R.drawable.ic_pending)
            SyncStatus.FAILED          -> Triple("Failed",         R.color.sync_deleting, R.drawable.ic_deleting)
            SyncStatus.DELETED_PENDING -> Triple("Delete Pending", R.color.sync_deleting, R.drawable.ic_deleting)
        }
        val color = ContextCompat.getColor(this, colorRes)
        val icon = ContextCompat.getDrawable(this, iconRes)!!.mutate()
        DrawableCompat.setTint(icon, color)
        binding.textDetailSyncStatus.text = label
        binding.textDetailSyncStatus.setTextColor(color)
        binding.textDetailSyncStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
        binding.textDetailSyncStatus.compoundDrawablePadding = 8

        val photoFile = report.photoPath?.let { File(it) }?.takeIf { it.exists() }
        if (photoFile != null) {
            binding.imageDetailPhoto.visibility = View.VISIBLE
            binding.imageDetailPhoto.setImageURI(Uri.fromFile(photoFile))
            binding.textNoAttachments.visibility = View.GONE
        } else {
            binding.imageDetailPhoto.visibility = View.GONE
            binding.textNoAttachments.visibility = View.VISIBLE
        }

        if (report.syncStatus == SyncStatus.DELETED_PENDING) {
            binding.buttonModify.visibility = View.GONE
            binding.buttonDetailAction.text = "Restore"
            binding.buttonDetailAction.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sync_pending))
            binding.buttonDetailAction.setOnClickListener { restoreReport(report) }
        } else {
            binding.buttonModify.visibility = View.VISIBLE
            binding.buttonModify.setOnClickListener {
                startActivity(
                    Intent(this, AddReportActivity::class.java)
                        .putExtra(AddReportActivity.EXTRA_REPORT_ID, report.id)
                )
            }
            binding.buttonDetailAction.text = "Delete"
            binding.buttonDetailAction.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sync_deleting))
            binding.buttonDetailAction.setOnClickListener { confirmDelete(report) }
        }
    }

    private fun confirmDelete(report: Report) {
        AlertDialog.Builder(this)
            .setTitle("Remove Report")
            .setMessage("Are you sure you want to remove \"${report.title}\"?")
            .setPositiveButton("Remove") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        val current = dao.getById(report.id) ?: return@withContext
                        dao.update(current.copy(
                            syncStatus = SyncStatus.DELETED_PENDING,
                            updatedAt = System.currentTimeMillis()
                        ))
                    }
                    // if sync deleted, finish the screen
                    finish()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun restoreReport(report: Report) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                dao.update(report.copy(
                    syncStatus = SyncStatus.PENDING,
                    updatedAt = System.currentTimeMillis()
                ))
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_REPORT_ID = "extra_report_id"
    }
}