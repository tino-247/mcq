package com.example.mcqapp.ui

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.mcqapp.R
import com.example.mcqapp.data.AppDatabase // For AppDatabase.DATABASE_NAME and closeAndClearInstance
import com.example.mcqapp.data.Question // For CSV export
import com.example.mcqapp.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Context // For SharedPreferences
import android.content.SharedPreferences

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: QuizViewModel by viewModels()

    private var selectedCategory: String? = null
    private var selectedSubCategory: String? = null

    // For EXPORTING files
    private lateinit var createFileLauncher: ActivityResultLauncher<Intent>
    private enum class ExportType { CSV, SQLITE_DB }
    private var currentExportType: ExportType? = null

    // For IMPORTING files
    private lateinit var openFileLauncher: ActivityResultLauncher<Array<String>>
    private enum class ImportMode { CSV, SQLITE_DB }
    private var currentImportMode: ImportMode? = null

    // For SharedPreferences
    private lateinit var sharedPreferences: SharedPreferences
    private val PREFS_NAME = "McqAppPrefs"
    private val KEY_WEAK_THRESHOLD = "weakThreshold"
    private val DEFAULT_WEAK_THRESHOLD = 3


    companion object {
        private const val TAG = "MainActivity"
        private const val TAG_EXPORT_IMPORT = "DB_OPS" // Combined tag
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "onCreate")

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadWeakThreshold() // Load saved threshold

        setupSpinners()

        binding.btnStartFullQuiz.setOnClickListener {
            val threshold = getWeakThresholdFromInput()
            saveWeakThreshold(threshold) // Save current value
            startQuizActivity(
                category = null,
                subCategory = null,
                onlyWeak = binding.cbOnlyWeak.isChecked,
                recentStreakThreshold = threshold
            )
        }

        binding.btnStartFilteredQuiz.setOnClickListener {
            if (selectedCategory == null && !binding.cbOnlyWeak.isChecked) {
                Toast.makeText(this, "Please select a category or check 'Only Weak Questions'", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val threshold = getWeakThresholdFromInput()
            saveWeakThreshold(threshold) // Save current value
            startQuizActivity(
                category = selectedCategory,
                subCategory = selectedSubCategory,
                onlyWeak = binding.cbOnlyWeak.isChecked,
                recentStreakThreshold = threshold
            )
        }


        // --- Other Action Button Listeners ---
        binding.btnViewStatistics.setOnClickListener {
            startActivity(Intent(this, StatisticsActivity::class.java))
        }

        binding.btnClearDatabase.setOnClickListener {
            showClearDatabaseConfirmationDialog()
        }

        binding.btnExportDatabase.setOnClickListener { // CSV Export
            currentExportType = ExportType.CSV
            initiateCsvExport()
        }

        binding.btnExportSqliteDb.setOnClickListener { // SQLite DB Export
            currentExportType = ExportType.SQLITE_DB
            initiateSqliteDbExport()
        }

        binding.btnImportDatabaseCsv.setOnClickListener { // CSV Import
            currentImportMode = ImportMode.CSV
            // Try more MIME types or a wildcard
            initiateFileImport(arrayOf(
                "text/csv",
                "text/comma-separated-values",
                "application/csv", // Another common one
                "text/plain",      // If it's a plain text file
                "text/*"           // Broader text wildcard
                // For extreme testing: "*/*" (allow all files - use carefully)
            ))

        }

        binding.btnImportSqliteDb.setOnClickListener { // SQLite DB Import
            currentImportMode = ImportMode.SQLITE_DB
            initiateFileImport(arrayOf("application/octet-stream", "application/x-sqlite3", "*/*"))
        }


        // --- ActivityResultLaunchers ---
        createFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    Log.d(TAG_EXPORT_IMPORT, "File creation successful for $currentExportType. URI: $uri")
                    when (currentExportType) {
                        ExportType.CSV -> {
                            lifecycleScope.launch {
                                val questions = viewModel.getAllQuestionsForExport()
                                writeQuestionsToCsv(uri, questions)
                            }
                        }
                        ExportType.SQLITE_DB -> {
                            lifecycleScope.launch {
                                copySqliteDb(uri)
                            }
                        }
                        null -> Log.e(TAG_EXPORT_IMPORT, "currentExportType is null.")
                    }
                } ?: Log.e(TAG_EXPORT_IMPORT, "File URI is null after successful creation.")
            } else {
                Log.d(TAG_EXPORT_IMPORT, "File creation cancelled/failed for $currentExportType. Code: ${result.resultCode}")
                Toast.makeText(this, "Export cancelled or failed.", Toast.LENGTH_SHORT).show()
            }
            currentExportType = null // Reset
        }

        openFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                Log.d(TAG_EXPORT_IMPORT, "File selected for import ($currentImportMode). URI: $it")
                when (currentImportMode) {
                    ImportMode.CSV -> showImportCsvConfirmationDialog(it)
                    ImportMode.SQLITE_DB -> showImportSqliteDbConfirmationDialog(it)
                    null -> Toast.makeText(this, "Import mode not set.", Toast.LENGTH_SHORT).show()
                }
            } ?: run {
                Log.d(TAG_EXPORT_IMPORT, "No file selected for import or URI is null.")
                Toast.makeText(this, "Import cancelled or no file selected.", Toast.LENGTH_SHORT).show()
            }
            currentImportMode = null // Reset
        }

        // --- ViewModel Observers for Import Feedback ---
        viewModel.importStatus.observe(this) { result -> // For CSV Import
            val success = result.first
            val errorMessage = result.second
            if (success) {
                Toast.makeText(this, "CSV Database imported successfully! Reloading data...", Toast.LENGTH_LONG).show()
                recreate() // Reload activity to refresh all data sources
            } else {
                Toast.makeText(this, "CSV Import failed: ${errorMessage ?: "Unknown error"}", Toast.LENGTH_LONG).show()
            }
        }

        viewModel.dbImportStatus.observe(this) { result -> // For SQLite DB Import
            val success = result.first
            val errorMessage = result.second
            if (success) {
                Toast.makeText(this, "SQLite DB imported successfully! App will now reload.", Toast.LENGTH_LONG).show()
                recreate() // Reload activity to refresh all data sources
            } else {
                Toast.makeText(this, "SQLite DB Import failed: ${errorMessage ?: "Unknown error"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupSpinners() {
        // ... (your existing spinner setup logic) ...
        val placeholderCategoryList = listOf("Loading categories...")
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, placeholderCategoryList)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCategory.adapter = categoryAdapter
        binding.spinnerCategory.isEnabled = false


        viewModel.allCategories.observe(this) { categories ->
            val categoryListWithOptions = mutableListOf<String>()
            if (categories.isNotEmpty()) {
                categoryListWithOptions.add("All Categories")
                categoryListWithOptions.addAll(categories)
                binding.spinnerCategory.isEnabled = true
            } else {
                categoryListWithOptions.add("No Categories Found")
                binding.spinnerCategory.isEnabled = false
            }

            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categoryListWithOptions)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerCategory.adapter = adapter
            if (categoryListWithOptions.contains("All Categories")) {
                binding.spinnerCategory.setSelection(adapter.getPosition("All Categories"))
            } else if (categoryListWithOptions.isNotEmpty()) {
                binding.spinnerCategory.setSelection(0)
            }
        }

        binding.spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selection = parent?.getItemAtPosition(position) as? String
                selectedCategory = if (selection == "All Categories" || selection == "No Categories Found" || selection == "Loading categories...") null else selection
                selectedSubCategory = null
                setupSubCategorySpinner(selectedCategory)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedCategory = null
                selectedSubCategory = null
                setupSubCategorySpinner(null)
            }
        }
        setupSubCategorySpinner(null)
    }

    private fun setupSubCategorySpinner(category: String?) {
        // ... (your existing sub-category spinner logic) ...
        val placeholderSubcategoryList = listOf("Select Category First")
        var subCategoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, placeholderSubcategoryList)
        subCategoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSubCategory.adapter = subCategoryAdapter
        binding.spinnerSubCategory.isEnabled = false

        if (category == null) {
            binding.spinnerSubCategory.isEnabled = false
            val allSub = listOf("All Subcategories (No Category Filter)")
            val ad = ArrayAdapter(this, android.R.layout.simple_spinner_item, allSub)
            ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerSubCategory.adapter = ad
            binding.spinnerSubCategory.setSelection(0)
            selectedSubCategory = null
            return
        }

        viewModel.getSubcategories(category).observe(this) { subcategories ->
            val subCategoryListWithOptions = mutableListOf<String>()
            if (subcategories.isNotEmpty()) {
                subCategoryListWithOptions.add("All Subcategories")
                subCategoryListWithOptions.addAll(subcategories)
                binding.spinnerSubCategory.isEnabled = true
            } else {
                subCategoryListWithOptions.add("No Subcategories Found")
                binding.spinnerSubCategory.isEnabled = false
            }

            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, subCategoryListWithOptions)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerSubCategory.adapter = adapter

            if (subCategoryListWithOptions.contains("All Subcategories")) {
                binding.spinnerSubCategory.setSelection(adapter.getPosition("All Subcategories"))
            } else if (subCategoryListWithOptions.isNotEmpty()){
                binding.spinnerSubCategory.setSelection(0)
            }

            binding.spinnerSubCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selection = parent?.getItemAtPosition(position) as? String
                    selectedSubCategory = if (selection == "All Subcategories" || selection == "No Subcategories Found" || selection == "All Subcategories (No Category Filter)") null else selection
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {
                    selectedSubCategory = null
                }
            }
        }
    }

    private fun getWeakThresholdFromInput(): Int {
        return binding.etWeakThreshold.text.toString().toIntOrNull() ?: DEFAULT_WEAK_THRESHOLD
    }

    private fun saveWeakThreshold(threshold: Int) {
        sharedPreferences.edit().putInt(KEY_WEAK_THRESHOLD, threshold).apply()
    }

    private fun loadWeakThreshold() {
        val savedThreshold = sharedPreferences.getInt(KEY_WEAK_THRESHOLD, DEFAULT_WEAK_THRESHOLD)
        binding.etWeakThreshold.setText(savedThreshold.toString())
    }


    // Update startQuizActivity to accept the new parameter
    private fun startQuizActivity(
        category: String?,
        subCategory: String?,
        onlyWeak: Boolean,
        recentStreakThreshold: Int // New parameter
    ) {
        val intent = Intent(this, QuizActivity::class.java).apply {
            putExtra(QuizActivity.EXTRA_CATEGORY, category)
            putExtra(QuizActivity.EXTRA_SUB_CATEGORY, subCategory)
            putExtra(QuizActivity.EXTRA_ONLY_WEAK, onlyWeak)
            putExtra(QuizActivity.EXTRA_RECENT_STREAK_THRESHOLD, recentStreakThreshold) // New extra
        }
        startActivity(intent)
    }

    // --- EXPORT Logic ---
    private fun initiateCsvExport() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "mcq_export_$timestamp.csv"
        launchCreateFileIntent(fileName, "text/csv")
    }

    private fun initiateSqliteDbExport() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${AppDatabase.DATABASE_NAME}_backup_$timestamp.db"
        launchCreateFileIntent(fileName, "application/octet-stream")
    }

    private fun launchCreateFileIntent(fileName: String, mimeType: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, fileName)
            // Optionally: putExtra(DocumentsContract.EXTRA_INITIAL_URI, MediaStore.Downloads.EXTERNAL_CONTENT_URI)
        }
        try {
            createFileLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG_EXPORT_IMPORT, "Error launching file creation intent for $mimeType", e)
            Toast.makeText(this, "Error: Cannot open file picker. ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun writeQuestionsToCsv(uri: Uri, questions: List<Question>) {
        withContext(Dispatchers.IO) {
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream, "UTF-8").use { writer -> // Specify UTF-8
                        writer.append("Kategorie,Unter-Kategorie,Frage #,Frage,Antwort A,Antwort B,Antwort C,Antwort D,Richtige Antwort,Abbildung,TimesAnswered,TimesCorrect,TimesChosenA,TimesChosenB,TimesChosenC,TimesChosenD,timesCorrectRecent\n")
                        questions.forEach { q ->
                            writer.append("\"${q.category.csvEscape()}\",")
                            writer.append("\"${q.subCategory.csvEscape()}\",")
                            writer.append("\"${q.questionNumber.csvEscape()}\",")
                            writer.append("\"${q.text.csvEscape()}\",")
                            writer.append("\"${q.optionA.csvEscape()}\",")
                            writer.append("\"${q.optionB.csvEscape()}\",")
                            writer.append("\"${q.optionC.csvEscape()}\",")
                            writer.append("\"${q.optionD.csvEscape()}\",")
                            writer.append("\"${q.correctAnswer.csvEscape()}\",")
                            writer.append("\"${(q.imageName ?: "").csvEscape()}\",")
                            writer.append("${q.timesAnswered},")
                            writer.append("${q.timesCorrect},")
                            writer.append("${q.timesChosenA},")
                            writer.append("${q.timesChosenB},")
                            writer.append("${q.timesChosenC},")
                            writer.append("${q.timesChosenD},")
                            writer.append("${q.timesCorrectRecent}\n")
                        }
                        writer.flush()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "CSV exported successfully.", Toast.LENGTH_LONG).show()
                        }
                    }
                } ?: throw IOException("Failed to open output stream for URI: $uri")
            } catch (e: Exception) { // Catch generic Exception
                Log.e(TAG_EXPORT_IMPORT, "Error during CSV writing", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error writing CSV: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun copySqliteDb(destinationUri: Uri) {
        withContext(Dispatchers.IO) {
            val dbFile = applicationContext.getDatabasePath(AppDatabase.DATABASE_NAME)
            if (!dbFile.exists()) {
                Log.e(TAG_EXPORT_IMPORT, "Source DB file not found: ${dbFile.absolutePath}")
                withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "Error: Source DB not found.", Toast.LENGTH_LONG).show() }
                return@withContext
            }
            try {
                // Attempt WAL checkpoint
                AppDatabase.getDatabase(applicationContext).openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL);")
                Log.d(TAG_EXPORT_IMPORT, "WAL checkpoint attempted for DB export.")
                // Introduce a small delay for safety after checkpoint, though ideally not needed
                // kotlinx.coroutines.delay(200)

                FileInputStream(dbFile).use { inputStream ->
                    contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                        inputStream.copyTo(outputStream)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "SQLite DB exported successfully.", Toast.LENGTH_LONG).show()
                        }
                    } ?: throw IOException("Failed to open output stream for destination URI: $destinationUri")
                }
            } catch (e: Exception) {
                Log.e(TAG_EXPORT_IMPORT, "Error during SQLite DB copy", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error copying DB: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun String.csvEscape(): String {
        return this.replace("\"", "\"\"")
    }

    // --- IMPORT Logic ---
    private fun initiateFileImport(mimeTypes: Array<String>) {
        try {
            Log.d(TAG_EXPORT_IMPORT, "Launching file picker for import with MIME types: ${mimeTypes.joinToString()}")
            openFileLauncher.launch(mimeTypes)
        } catch (e: Exception) {
            Log.e(TAG_EXPORT_IMPORT, "Error launching file open intent for import", e)
            Toast.makeText(this, "Error: Cannot open file picker. ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showImportCsvConfirmationDialog(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("Confirm CSV Import")
            .setMessage("This will clear all current data and replace it from the selected CSV. Proceed?")
            .setPositiveButton("Import CSV") { _, _ ->
                viewModel.importDatabaseFromCsv(uri) // From QuizViewModel
            }
            .setNegativeButton("Cancel", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun showImportSqliteDbConfirmationDialog(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("Confirm SQLite DB Import")
            .setMessage("WARNING: This will REPLACE your current database with the selected file. All current data will be lost. The app will attempt to reload. Ensure the selected file is a valid MCQ App database. Proceed?")
            .setPositiveButton("Import DB") { _, _ ->
                viewModel.importSqliteDatabaseFile(uri, applicationContext) // From QuizViewModel
            }
            .setNegativeButton("Cancel", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    // --- Other Dialogs ---
    private fun showClearDatabaseConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Confirm Clear Database")
            .setMessage("Are you sure you want to delete all questions and statistics? This action cannot be undone.")
            .setPositiveButton("Clear Data") { _, _ ->
                viewModel.clearDatabase()
                Toast.makeText(this, "Database cleared. App may reload data on next start.", Toast.LENGTH_SHORT).show()
                // Consider calling recreate() here as well if you want immediate UI refresh to empty state
            }
            .setNegativeButton("Cancel", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }
}