package com.example.status_saver.activities

import android.animation.ValueAnimator
import android.app.Activity
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.example.status_saver.GalleryState
import com.example.status_saver.R
import com.example.status_saver.Source
import com.example.status_saver.StatusItem
import com.example.status_saver.StatusPaths
import com.example.status_saver.classes.GalleryViewModel
import com.example.status_saver.adapters.StatusAdapter
import com.example.status_saver.databinding.FragmentGalleryBinding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** Shows either the live statuses ("Status" tab) or the saved ones ("Saved" tab). */
class GalleryFragment : Fragment(R.layout.fragment_gallery) {

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!

    private val vm: GalleryViewModel by viewModels()
    private lateinit var adapter: StatusAdapter
    private lateinit var backCallback: OnBackPressedCallback

    private var pendingSource: Source? = null

    private val pickFolder =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val pickedSource = pendingSource
            pendingSource = null
            val uri = result.data?.data
            if (result.resultCode == Activity.RESULT_OK && uri != null && pickedSource != null) {
                viewLifecycleOwner.lifecycleScope.launch {
                    if (!vm.onFolderPicked(uri, pickedSource)) {
                        toast(getString(R.string.wrong_folder, pickedSource.label))
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingSource = Source.values().firstOrNull {
            it.name == savedInstanceState?.getString(KEY_PENDING_SOURCE)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PENDING_SOURCE, pendingSource?.name)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentGalleryBinding.bind(view)

        vm.tab = requireArguments().getInt(ARG_PAGE)
        vm.start(savedScreen = vm.tab == 2)
        val savedScreen = vm.isSavedScreen

        adapter = StatusAdapter(
            onClick = { item ->
                if (!vm.bulkRunning.value) {
                    if (vm.selection.value.isNotEmpty()) vm.toggle(item) else openPreview(item)
                }
            },
            onLongClick = { item -> vm.toggle(item) },
            onSave = { item ->
                vm.saveOne(item) { ok ->
                    toast(getString(if (ok) R.string.saved_ok else R.string.saved_failed))
                }
            },
        )
        binding.list.layoutManager = GridLayoutManager(
            requireContext(),
            if (resources.configuration.screenWidthDp >= 600) 3 else 2
        )
        binding.list.adapter = adapter

        setUpSourceToggle(savedScreen)

        binding.swipe.setOnChildScrollUpCallback { _, _ -> binding.list.canScrollVertically(-1) }
        binding.swipe.setOnRefreshListener { vm.refresh() }
        binding.btnChoose.setOnClickListener {
            if (pendingSource != null) return@setOnClickListener
            pendingSource = vm.source
            try {
                pickFolder.launch(StatusPaths.pickerIntent(vm.source))
            } catch (_: ActivityNotFoundException) {
                pendingSource = null
                toast(getString(R.string.folder_picker_unavailable))
            }
        }
        binding.btnRefresh.setOnClickListener { vm.refresh() }
        binding.selectionBar.setNavigationOnClickListener { vm.clearSelection() }
        binding.selectionBar.inflateMenu(R.menu.menu_gallery)
        val bulkAction = binding.selectionBar.menu.add(Menu.NONE, BULK_ACTION, Menu.NONE, R.string.save)
        bulkAction.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        binding.selectionBar.setOnMenuItemClickListener {
            when (it.itemId) {
                BULK_ACTION -> onBulkClick()
                R.id.action_select_all -> vm.selectAll()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }

        // Back clears the selection first, then leaves the screen.
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                val activity = requireActivity() as MainActivity
                if (activity.settingsOpen) {
                    activity.supportFragmentManager.popBackStack()
                } else {
                    if (!vm.bulkRunning.value) vm.clearSelection()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        val toolbar = requireActivity().findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, inflater: MenuInflater) = Unit

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    if (menuItem.itemId != R.id.action_select_all) return false
                    vm.selectAll()
                    return true
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.state.collect { render() } }
                launch { vm.selection.collect { render() } }
                launch { vm.bulkRunning.collect { render() } }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // New statuses appear as the user views them in WhatsApp.
        vm.syncSource()
        if (!vm.isSavedScreen) {
            binding.sourceToggle.check(
                if (vm.source == Source.BUSINESS) R.id.btnBusiness else R.id.btnWhatsapp
            )
        }
        vm.refresh()
    }

    override fun onPause() {
        backCallback.isEnabled = false
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ------------------------------------------------------------------ setup

    private fun setUpSourceToggle(savedScreen: Boolean) {
        binding.sourceToggle.isVisible = !savedScreen
        binding.sourceToggle.check(
            if (vm.source == Source.BUSINESS) R.id.btnBusiness else R.id.btnWhatsapp
        )
        binding.sourceToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                vm.selectSource(
                    if (checkedId == R.id.btnBusiness) Source.BUSINESS else Source.WHATSAPP
                )
            }
        }
    }

    // ------------------------------------------------------------------ drawing

    private fun render() {
        val b = _binding ?: return
        val state = vm.state.value
        val selection = vm.selection.value
        val items = vm.currentItems()

        adapter.submitList(items)
        adapter.setSelection(selection)

        val loading = state == GalleryState.Loading
        if (!loading) b.swipe.isRefreshing = false
        b.progress.isVisible = loading && !b.swipe.isRefreshing

        val showMessage = !loading && state !is GalleryState.Success
        showWithFade(b.emptyGroup, showMessage)
        b.list.isVisible = !showMessage
        if (showMessage) renderMessage(state)

        val count = selection.size
        val busy = vm.bulkRunning.value
        val selecting = count > 0 || busy
        b.swipe.isEnabled = !selecting
        b.sourceToggle.isVisible = !vm.isSavedScreen && !selecting
        showWithFade(b.selectionBar, selecting)
        b.selectionBar.title = getString(R.string.selection_count, count)
        val bulk = b.selectionBar.menu.findItem(BULK_ACTION)
        bulk.setTitle(if (vm.isSavedScreen) R.string.delete else R.string.save)
        bulk.isEnabled = !busy && items.isNotEmpty()
        b.selectionBar.menu.findItem(R.id.action_select_all).isEnabled = !busy
        b.selectionBar.navigationIcon = if (busy) null else
            ContextCompat.getDrawable(b.root.context, R.drawable.ic_back)
        b.bulkProgress.isVisible = busy
        backCallback.isEnabled = count > 0 && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    }

    private fun showWithFade(view: View, visible: Boolean) {
        if (view.isVisible == visible) return
        view.animate().cancel()
        view.isVisible = visible
        view.alpha = 1f
        if (visible && ValueAnimator.areAnimatorsEnabled()) {
            view.alpha = 0f
            view.animate().alpha(1f).setDuration(160).start()
        }
    }

    private fun renderMessage(state: GalleryState) {
        val b = binding
        val appName = vm.source.label
        val needsFolder = state == GalleryState.NeedsPermission
        when {
            state == GalleryState.Error -> {
                b.emptyTitle.setText(R.string.gallery_error_title)
                b.emptyText.setText(R.string.gallery_error_text)
            }
            needsFolder -> {
                b.emptyTitle.setText(R.string.need_folder_title)
                b.emptyText.text = getString(R.string.need_folder_text, appName)
            }
            vm.isSavedScreen -> {
                b.emptyTitle.setText(R.string.empty_saved_title)
                b.emptyText.setText(R.string.empty_saved_text)
            }
            else -> {
                b.emptyTitle.setText(R.string.empty_title)
                b.emptyText.text = getString(R.string.empty_text, appName)
            }
        }
        b.emptyIllustration.setImageResource(
            when {
                needsFolder -> R.drawable.ic_empty_folder
                vm.isSavedScreen -> R.drawable.ic_nav_saved
                else -> R.drawable.ic_nav_status
            }
        )
        b.btnChoose.isVisible = needsFolder
        b.btnRefresh.isVisible = !needsFolder
    }

    // ------------------------------------------------------------------ actions

    private fun openPreview(item: StatusItem) {
        val items = vm.currentItems()
        val index = items.indexOfFirst { it.uri == item.uri }.coerceAtLeast(0)
        startActivity(PreviewActivity.intent(requireContext(), ArrayList(items), index, vm.source))
    }

    private fun onBulkClick() {
        if (vm.isSavedScreen) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_title)
                .setMessage(getString(R.string.delete_message, vm.selection.value.size))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ ->
                    vm.deleteSelected { ok, failed ->
                        if (isAdded) toast(getString(R.string.deleted_count, ok) + failedSuffix(failed))
                    }
                }
                .show()
        } else {
            vm.saveSelected { ok, failed ->
                if (isAdded) toast(getString(R.string.saved_count, ok) + failedSuffix(failed))
            }
        }
    }

    private fun failedSuffix(failed: Int) =
        if (failed > 0) getString(R.string.failed_suffix, failed) else ""

    private fun toast(message: String) =
        context?.let { Toast.makeText(it, message, Toast.LENGTH_SHORT).show() }

    companion object {
        private const val BULK_ACTION = 1
        private const val KEY_PENDING_SOURCE = "pending_folder_source"
        private const val ARG_PAGE = "page"

        fun newInstance(page: Int) = GalleryFragment().apply {
            arguments = bundleOf(ARG_PAGE to page)
        }
    }
}