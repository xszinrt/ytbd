package com.arabi.ytdownloader.ui.preview

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.arabi.ytdownloader.data.ExtractionResult
import com.arabi.ytdownloader.databinding.FragmentPreviewBinding
import com.arabi.ytdownloader.service.DownloadForegroundService

class PreviewFragment : Fragment() {

    private var _binding: FragmentPreviewBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: VideoSelectionAdapter

    private var playlistId: String? = null
    private var playlistTitle: String? = null

    companion object {
        private const val ARG_IDS = "ids"
        private const val ARG_TITLES = "titles"
        private const val ARG_URLS = "urls"
        private const val ARG_QUEUED = "queued"
        private const val ARG_PLAYLIST_ID = "playlist_id"
        private const val ARG_PLAYLIST_TITLE = "playlist_title"

        // Common progressive/muxed quality presets; "best" lets yt-dlp pick
        // the best pre-merged format available without invoking ffmpeg merge.
        val QUALITY_OPTIONS = listOf(
            "best" to "أفضل جودة متاحة",
            "bestvideo[height<=1080]+bestaudio/best[height<=1080]" to "1080p",
            "bestvideo[height<=720]+bestaudio/best[height<=720]" to "720p",
            "bestvideo[height<=480]+bestaudio/best[height<=480]" to "480p",
            "bestaudio/best" to "صوت فقط (MP3)"
        )

        fun newInstance(result: ExtractionResult, alreadyQueuedIds: Set<String>): PreviewFragment {
            val fragment = PreviewFragment()
            val ids = ArrayList<String>()
            val titles = ArrayList<String>()
            val urls = ArrayList<String>()

            when (result) {
                is ExtractionResult.SingleVideo -> {
                    ids.add(result.entry.id)
                    titles.add(result.entry.title)
                    urls.add(result.entry.url ?: "")
                }
                is ExtractionResult.Playlist -> {
                    result.entries.forEach { entry ->
                        ids.add(entry.id)
                        titles.add(entry.title)
                        urls.add(entry.url ?: "https://www.youtube.com/watch?v=${entry.id}")
                    }
                }
            }

            fragment.arguments = Bundle().apply {
                putStringArrayList(ARG_IDS, ids)
                putStringArrayList(ARG_TITLES, titles)
                putStringArrayList(ARG_URLS, urls)
                putStringArrayList(ARG_QUEUED, ArrayList(alreadyQueuedIds))
                if (result is ExtractionResult.Playlist) {
                    putString(ARG_PLAYLIST_ID, result.playlistId)
                    putString(ARG_PLAYLIST_TITLE, result.playlistTitle)
                }
            }
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ids = arguments?.getStringArrayList(ARG_IDS).orEmpty()
        val titles = arguments?.getStringArrayList(ARG_TITLES).orEmpty()
        val urls = arguments?.getStringArrayList(ARG_URLS).orEmpty()
        val queued = arguments?.getStringArrayList(ARG_QUEUED).orEmpty().toSet()
        playlistId = arguments?.getString(ARG_PLAYLIST_ID)
        playlistTitle = arguments?.getString(ARG_PLAYLIST_TITLE)

        val items = ids.indices.map { i ->
            PreviewItem(
                id = ids[i],
                title = titles.getOrElse(i) { ids[i] },
                url = urls.getOrElse(i) { "" },
                alreadyQueued = ids[i] in queued,
                checked = ids[i] !in queued
            )
        }.toMutableList()

        adapter = VideoSelectionAdapter(items) { updateDownloadButtonLabel(items) }
        binding.videoRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.videoRecyclerView.adapter = adapter

        val qualityLabels = QUALITY_OPTIONS.map { it.second }
        binding.qualitySpinner.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, qualityLabels
        )

        binding.selectAllCheckbox.setOnCheckedChangeListener { _, checked ->
            adapter.setAllChecked(checked)
        }

        updateDownloadButtonLabel(items)

        binding.downloadButton.setOnClickListener {
            startDownload()
        }
    }

    private fun updateDownloadButtonLabel(items: List<PreviewItem>) {
        val selectedCount = items.count { it.checked && !it.alreadyQueued }
        binding.downloadButton.text = if (selectedCount == items.size) {
            getString(com.arabi.ytdownloader.R.string.action_download_all)
        } else {
            "${getString(com.arabi.ytdownloader.R.string.action_download_selected)} ($selectedCount)"
        }
        binding.downloadButton.isEnabled = selectedCount > 0
    }

    private fun startDownload() {
        val selected = adapter.getSelectedItems()
        if (selected.isEmpty()) return

        val qualityFormat = QUALITY_OPTIONS[binding.qualitySpinner.selectedItemPosition].first

        val intent = Intent(requireContext(), DownloadForegroundService::class.java).apply {
            action = DownloadForegroundService.ACTION_ENQUEUE
            putStringArrayListExtra(DownloadForegroundService.EXTRA_IDS, ArrayList(selected.map { it.id }))
            putStringArrayListExtra(DownloadForegroundService.EXTRA_TITLES, ArrayList(selected.map { it.title }))
            putStringArrayListExtra(DownloadForegroundService.EXTRA_URLS, ArrayList(selected.map { it.url }))
            putExtra(DownloadForegroundService.EXTRA_QUALITY, qualityFormat)
            putExtra(DownloadForegroundService.EXTRA_PLAYLIST_ID, playlistId)
            putExtra(DownloadForegroundService.EXTRA_PLAYLIST_TITLE, playlistTitle)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(requireContext(), intent)
        } else {
            requireContext().startService(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
