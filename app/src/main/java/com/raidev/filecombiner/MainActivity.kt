package com.raidev.filecombiner

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.raidev.filecombiner.databinding.ActivityMainBinding
import java.io.InputStream
import java.io.OutputStream
import java.util.logging.Logger

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val logger = Logger.getLogger(MainActivity::class.java.name)

    private var selectedUris: List<Uri> = emptyList()
    private var selectedExtractUri: Uri? = null
    private var fileSizeBytes: Int = 0

    private val pickMedia = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { intent ->
                val clipData = intent.clipData
                val uriList = mutableListOf<Uri>()
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        uriList.add(clipData.getItemAt(i).uri)
                    }
                } else {
                    intent.data?.let { uri ->
                        uriList.add(uri)
                    }
                }
                handleSelectedFiles(uriList)
            }
        }
    }

    private val selectFileForExtracting = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { intent ->
                selectedExtractUri = intent.data
                binding.txtMessages.text = "File selected for extracting: ${selectedExtractUri?.path ?: "Unknown Path"}"
            }
        }
    }

    private fun handleSelectedFiles(uriList: List<Uri>) {
        binding.txtMessages.text = "Files selected: ${uriList.joinToString { it.path ?: "Unknown Path" }}"
        selectedUris = uriList
        displayFileSizes(uriList)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        binding.btnSelectFiles.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            pickMedia.launch(intent)
        }

        binding.btnCombine.setOnClickListener {
            if (selectedUris.isEmpty()) {
                binding.txtMessages.text = "Please select files first"
                return@setOnClickListener
            }

            val combinedFileName = buildCombinedFileName(selectedUris)
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_TITLE, combinedFileName)
            }
            startActivityForResult(intent, REQUEST_CODE_CREATE_FILE)
        }

        binding.btnSelectFileForExtracting.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
            }
            selectFileForExtracting.launch(intent)
        }

        binding.btnExtract.setOnClickListener {
            val fileSizeInput = binding.editTextFileSize.text.toString()
            if (fileSizeInput.isBlank()) {
                binding.txtMessages.text = "Please enter the file size for extraction (e.g., 1MB)"
                return@setOnClickListener
            }

            fileSizeBytes = try {
                parseSize(fileSizeInput)
            } catch (e: IllegalArgumentException) {
                binding.txtMessages.text = e.message
                return@setOnClickListener
            }

            val intent1 = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_TITLE, "extracted_file_1.${getFileExtensionFromUri(selectedUris[0])}")
            }
            startActivityForResult(intent1, REQUEST_CODE_CREATE_FIRST_EXTRACT_FILE)
        }

        binding.btnCopyFileSizes.setOnClickListener {
            copyFileSizesToClipboard(selectedUris)
        }
    }

    private fun startExtractionOfSecondFile() {
        val intent2 = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_TITLE, "extracted_file_2.${getFileExtensionFromUri(selectedUris[1])}")
        }
        startActivityForResult(intent2, REQUEST_CODE_CREATE_SECOND_EXTRACT_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        logger.info("onActivityResult: requestCode=$requestCode, resultCode=$resultCode")
        if (resultCode == Activity.RESULT_OK && data != null) {
            try {
                when (requestCode) {
                    REQUEST_CODE_CREATE_FILE -> {
                        data.data?.let { uri ->
                            val outputStream = contentResolver.openOutputStream(uri)
                            if (outputStream != null) {
                                combineFiles(selectedUris, outputStream)
                            } else {
                                binding.txtMessages.text = "Failed to open output stream"
                                logger.warning("Failed to open output stream for combined file")
                            }
                        }
                    }
                    REQUEST_CODE_CREATE_FIRST_EXTRACT_FILE -> {
                        data.data?.let { uri ->
                            val outputStream = contentResolver.openOutputStream(uri)
                            if (outputStream != null) {
                                selectedExtractUri?.let { extractUri ->
                                    logger.info("Extracting first file: extractUri=$extractUri, fileSizeBytes=$fileSizeBytes")
                                    extractFirstFile(extractUri, fileSizeBytes, outputStream)
                                } ?: run {
                                    binding.txtMessages.text = "No file selected for extraction"
                                    logger.warning("No file selected for extraction")
                                }
                            } else {
                                binding.txtMessages.text = "Failed to open output stream"
                                logger.warning("Failed to open output stream for first extracted file")
                            }
                        }
                        startExtractionOfSecondFile()
                    }
                    REQUEST_CODE_CREATE_SECOND_EXTRACT_FILE -> {
                        data.data?.let { uri ->
                            val outputStream = contentResolver.openOutputStream(uri)
                            if (outputStream != null) {
                                selectedExtractUri?.let { extractUri ->
                                    logger.info("Extracting second file: extractUri=$extractUri, fileSizeBytes=$fileSizeBytes")
                                    extractSecondFile(extractUri, fileSizeBytes, outputStream)
                                } ?: run {
                                    binding.txtMessages.text = "No file selected for extraction"
                                    logger.warning("No file selected for extraction")
                                }
                            } else {
                                binding.txtMessages.text = "Failed to open output stream"
                                logger.warning("Failed to open output stream for second extracted file")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.severe("Exception occurred in onActivityResult: ${e.message}")
                runOnUiThread {
                    binding.txtMessages.text = "Error: ${e.message}"
                }
            }
        }
    }

    private fun combineFiles(uris: List<Uri>, outputStream: OutputStream) {
        try {
            outputStream.use { outStream ->
                for (uri in uris) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val buffer = ByteArray(1024)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outStream.write(buffer, 0, bytesRead)
                        }
                    } ?: run {
                        logger.warning("Failed to open input stream for URI: $uri")
                    }
                }
            }
            runOnUiThread {
                binding.txtMessages.text = "Files combined successfully."
            }
        } catch (e: Exception) {
            logger.severe("Exception occurred while combining files: ${e.message}")
            runOnUiThread {
                binding.txtMessages.text = "Error during combination: ${e.message}"
            }
        }
    }

    private fun extractFirstFile(uri: Uri, fileSizeBytes: Int, outputStream: OutputStream) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                // Extract the first file content
                val buffer = ByteArray(1024)
                var bytesRead: Int = 0
                var remainingBytes = fileSizeBytes
                outputStream.use { outStream ->
                    while (remainingBytes > 0 && inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (bytesRead > remainingBytes) {
                            outStream.write(buffer, 0, remainingBytes)
                            break
                        } else {
                            outStream.write(buffer, 0, bytesRead)
                            remainingBytes -= bytesRead
                        }
                    }
                }
            } ?: run {
                logger.warning("Failed to open input stream for URI: $uri")
            }
            runOnUiThread {
                binding.txtMessages.text = "First file extracted successfully"
            }
        } catch (e: Exception) {
            logger.severe("Exception occurred while extracting files: ${e.message}")
            runOnUiThread {
                binding.txtMessages.text = "Error during extraction: ${e.message}"
            }
        }
    }

    private fun extractSecondFile(uri: Uri, offsetBytes: Int, outputStream: OutputStream) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                // Skip the initial file content
                inputStream.skip(offsetBytes.toLong())

                // Extract the remaining file content
                val buffer = ByteArray(1024)
                var bytesRead: Int = 0
                outputStream.use { outStream ->
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outStream.write(buffer, 0, bytesRead)
                    }
                }
            } ?: run {
                logger.warning("Failed to open input stream for URI: $uri")
            }
            runOnUiThread {
                binding.txtMessages.text = "Second file extracted successfully"
            }
        } catch (e: Exception) {
            logger.severe("Exception occurred while extracting files: ${e.message}")
            runOnUiThread {
                binding.txtMessages.text = "Error during extraction: ${e.message}"
            }
        }
    }

    private fun displayFileSizes(uris: List<Uri>) {
        val fileSizeInfo = StringBuilder()
        for (uri in uris) {
            val size = getFileSize(uri)
            fileSizeInfo.append("File: ${uri.path ?: "Unknown Path"} Size: $size bytes\n")
        }
        binding.txtMessages.text = fileSizeInfo.toString()
    }

    private fun getFileSize(uri: Uri): Long {
        return contentResolver.openFileDescriptor(uri, "r")?.use {
            it.statSize
        } ?: 0
    }

    private fun getFileExtensionFromUri(uri: Uri): String {
        val mimeType = contentResolver.getType(uri)
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
    }

    private fun copyFileSizesToClipboard(uris: List<Uri>) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val fileSizeInfo = StringBuilder()
        for (uri in uris) {
            val size = getFileSize(uri)
            fileSizeInfo.append("File: ${uri.path ?: "Unknown Path"} Size: $size bytes\n")
        }
        val clip = ClipData.newPlainText("File Sizes", fileSizeInfo.toString())
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "File sizes copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun buildCombinedFileName(uris: List<Uri>): String {
        val sizesAndExtensions = uris.joinToString("_") { "${getFileSize(it)}B_${getFileExtensionFromUri(it)}" }
        return "combined_file_$sizesAndExtensions"
    }

    private fun parseSize(sizeStr: String): Int {
        if (sizeStr.isEmpty()) {
            throw IllegalArgumentException("Size cannot be empty")
        }
        return try {
            sizeStr.trim().uppercase().let {
                when {
                    it.endsWith("B") -> it.removeSuffix("B").trim().toFloat().toInt()
                    it.endsWith("KB") -> (it.removeSuffix("KB").trim().toFloat() * 1024).toInt()
                    it.endsWith("MB") -> (it.removeSuffix("MB").trim().toFloat() * 1024 * 1024).toInt()
                    it.endsWith("GB") -> (it.removeSuffix("GB").trim().toFloat() * 1024 * 1024 * 1024).toInt()
                    else -> it.toFloat().toInt() // Assume bytes if no suffix is provided
                }
            }
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid size format")
        }
    }

    companion object {
        private const val REQUEST_CODE_CREATE_FILE = 1
        private const val REQUEST_CODE_CREATE_FIRST_EXTRACT_FILE = 2
        private const val REQUEST_CODE_CREATE_SECOND_EXTRACT_FILE = 3
    }
}
