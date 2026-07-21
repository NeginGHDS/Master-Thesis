package com.example.offline_first.ui.screens.addreport

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.offline_first.R
import com.example.offline_first.data.local.AppDatabase
import com.example.offline_first.data.model.Report
import com.example.offline_first.data.model.SyncStatus
import com.example.offline_first.databinding.ActivityAddReportBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class AddReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddReportBinding
    private val dao by lazy { AppDatabase.getInstance(this).reportDao() }

    private var editingReport: Report? = null
    private var selectedPhotoPath: String? = null
    private var pendingCameraFile: File? = null

    private val categories = listOf(
        "Infrastructure",
        "Public Safety",
        "Vandalism",
        "Sanitation",
        "Environment",
        "Other"
    )

    // --- Activity result launchers ---

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { copyUriToLocalFile(it)?.also { path -> setPhoto(path) } }
        }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                pendingCameraFile?.absolutePath?.also { path -> setPhoto(path) }
            } else {
                pendingCameraFile?.delete()
                pendingCameraFile = null
            }
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchCamera()
            else Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }

    // ---------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityAddReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupCategoryDropdown()

        binding.buttonGallery.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        binding.buttonCamera.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                launchCamera()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        binding.buttonRemovePhoto.setOnClickListener {
            setPhoto(null)
        }

        binding.buttonSubmit.setOnClickListener {
            submitReport()
        }

        val reportId = intent.getLongExtra(EXTRA_REPORT_ID, -1L)
        if (reportId != -1L) {
            loadForEditing(reportId)
        } else {
            supportActionBar?.apply {
                title = getString(R.string.add_report)
                setDisplayHomeAsUpEnabled(true)
            }
        }
    }

    private fun launchCamera() {
        val photoFile = File(
            getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "photo_${System.currentTimeMillis()}.jpg"
        )
        pendingCameraFile = photoFile
        val uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.provider",
            photoFile
        )
        cameraLauncher.launch(uri)
    }

    private fun copyUriToLocalFile(uri: Uri): String? {
        return try {
            val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir
            val dest = File(dir, "photo_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(uri)?.use { it.copyTo(dest.outputStream()) }
            dest.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun setPhoto(path: String?) {
        selectedPhotoPath = path
        if (path != null) {
            binding.imagePhotoPreview.visibility = View.VISIBLE
            binding.imagePhotoPreview.setImageURI(Uri.fromFile(File(path)))
            binding.buttonRemovePhoto.visibility = View.VISIBLE
        } else {
            binding.imagePhotoPreview.visibility = View.GONE
            binding.imagePhotoPreview.setImageURI(null)
            binding.buttonRemovePhoto.visibility = View.GONE
        }
    }

    private fun loadForEditing(reportId: Long) {
        lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) { dao.getById(reportId) }
            if (report == null) { finish(); return@launch }
            editingReport = report

            supportActionBar?.apply {
                title = getString(R.string.edit_report)
                setDisplayHomeAsUpEnabled(true)
            }

            binding.inputTitle.editText?.setText(report.title)
            binding.inputDescription.editText?.setText(report.description)
            binding.dropdownCategory.setText(report.category, false)
            binding.inputLocation.editText?.setText(report.location)
            binding.buttonSubmit.text = getString(R.string.save_changes)

            report.photoPath?.takeIf { File(it).exists() }?.let { setPhoto(it) }
        }
    }

    private fun setupCategoryDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        binding.dropdownCategory.setAdapter(adapter)
    }

    private fun submitReport() {
        val title       = binding.inputTitle.editText?.text?.toString().orEmpty().trim()
        val description = binding.inputDescription.editText?.text?.toString().orEmpty().trim()
        val category    = binding.dropdownCategory.text?.toString().orEmpty().trim()
        val location    = binding.inputLocation.editText?.text?.toString().orEmpty().trim()

        if (title.isEmpty()) { binding.inputTitle.error = "Title is required"; return }
        binding.inputTitle.error = null

        if (description.isEmpty()) { binding.inputDescription.error = "Description is required"; return }
        binding.inputDescription.error = null

        if (category.isEmpty()) { binding.inputCategory.error = "Category is required"; return }
        binding.inputCategory.error = null

        if (location.isEmpty()) { binding.inputLocation.error = "Location is required"; return }
        binding.inputLocation.error = null

        binding.buttonSubmit.isEnabled = false

        val existing = editingReport
        if (existing != null) {
            val updated = existing.copy(
                title = title,
                description = description,
                category = category,
                location = location,
                photoPath = selectedPhotoPath,
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING
            )
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { dao.update(updated) }
                Toast.makeText(this@AddReportActivity, "Report updated.", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            val now = System.currentTimeMillis()
            val owner = FirebaseAuth.getInstance().currentUser?.email.orEmpty()
            val report = Report(
                localId = UUID.randomUUID().toString(),
                remoteId = null,
                title = title,
                description = description,
                category = category,
                location = location,
                photoPath = selectedPhotoPath,
                owner = owner,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.PENDING
            )
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { dao.insert(report) }
                Toast.makeText(this@AddReportActivity, "Report saved locally.", Toast.LENGTH_SHORT).show()
                finish()
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