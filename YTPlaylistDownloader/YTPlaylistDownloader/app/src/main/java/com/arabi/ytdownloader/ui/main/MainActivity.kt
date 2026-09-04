package com.arabi.ytdownloader.ui.main

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.arabi.ytdownloader.R
import com.arabi.ytdownloader.data.ExtractionResult
import com.arabi.ytdownloader.databinding.ActivityMainBinding
import com.arabi.ytdownloader.ui.preview.PreviewFragment
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: PlaylistExtractorViewModel

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[PlaylistExtractorViewModel::class.java]

        requestNotificationPermissionIfNeeded()
        setupListeners()
        observeState()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun setupListeners() {
        binding.extractButton.setOnClickListener {
            val url = binding.urlEditText.text?.toString()?.trim().orEmpty()
            if (url.isEmpty()) {
                binding.urlInputLayout.error = getString(R.string.error_empty_url)
                return@setOnClickListener
            }
            if (!url.contains("youtube.com") && !url.contains("youtu.be")) {
                binding.urlInputLayout.error = getString(R.string.error_not_youtube)
                return@setOnClickListener
            }
            binding.urlInputLayout.error = null
            viewModel.extract(url)
        }
    }

    private fun observeState() {
        viewModel.uiState.observe(this) { state ->
            when (state) {
                is ExtractUiState.Idle -> {
                    binding.loadingSpinner.visibility = android.view.View.GONE
                    binding.errorText.visibility = android.view.View.GONE
                }
                is ExtractUiState.Loading -> {
                    binding.loadingSpinner.visibility = android.view.View.VISIBLE
                    binding.errorText.visibility = android.view.View.GONE
                    binding.previewContainer.visibility = android.view.View.GONE
                }
                is ExtractUiState.Success -> {
                    binding.loadingSpinner.visibility = android.view.View.GONE
                    binding.previewContainer.visibility = android.view.View.VISIBLE
                    showPreview(state.result, state.alreadyQueuedIds)
                }
                is ExtractUiState.Error -> {
                    binding.loadingSpinner.visibility = android.view.View.GONE
                    binding.errorText.visibility = android.view.View.VISIBLE
                    binding.errorText.text = state.message
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showPreview(result: ExtractionResult, alreadyQueuedIds: Set<String>) {
        val fragment = PreviewFragment.newInstance(result, alreadyQueuedIds)
        supportFragmentManager.beginTransaction()
            .replace(R.id.previewContainer, fragment)
            .commit()
    }
}
