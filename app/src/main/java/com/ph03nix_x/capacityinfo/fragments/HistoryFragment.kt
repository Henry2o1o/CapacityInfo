package com.ph03nix_x.capacityinfo.fragments

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.ph03nix_x.capacityinfo.R
import com.ph03nix_x.capacityinfo.activities.MainActivity
import com.ph03nix_x.capacityinfo.adapters.HistoryAdapter
import com.ph03nix_x.capacityinfo.databases.HistoryDB
import com.ph03nix_x.capacityinfo.databinding.HistoryFragmentBinding
import com.ph03nix_x.capacityinfo.helpers.HistoryHelper
import com.ph03nix_x.capacityinfo.interfaces.PremiumInterface
import com.ph03nix_x.capacityinfo.interfaces.views.MenuInterface
import com.ph03nix_x.capacityinfo.utilities.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class HistoryFragment : Fragment(R.layout.history_fragment), MenuInterface {

    private var isResume = false

    private lateinit var pref: SharedPreferences
    lateinit var historyAdapter: HistoryAdapter

    var binding: HistoryFragmentBinding? = null

    companion object {
        @SuppressLint("StaticFieldLeak")
        var instance: HistoryFragment? = null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        binding = HistoryFragmentBinding.inflate(inflater, container, false)

        pref = PreferenceManager.getDefaultSharedPreferences(requireContext())

        return binding?.root?.rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        super.onViewCreated(view, savedInstanceState)

        instance = this

        val historyDB = HistoryDB(requireContext())

        if(!isInstalledFromGooglePlay() && PremiumInterface.isPremium &&
            historyDB.getCount() > 0)
            throw RuntimeException("Application not installed from Google Play")

        if(PremiumInterface.isPremium && historyDB.getCount() > 0) {
            binding?.apply {
                emptyHistoryLayout.isVisible = false
                refreshEmptyHistory.isVisible = false
                historyRecyclerView.isVisible = true
                refreshHistory.isVisible = true
                historyAdapter = HistoryAdapter(historyDB.readDB())
                historyRecyclerView.setItemViewCacheSize(historyAdapter.itemCount)
                historyRecyclerView.adapter = historyAdapter
            }
        }
        else if(PremiumInterface.isPremium) {
            binding?.apply {
                historyRecyclerView.isVisible = false
                refreshEmptyHistory.isVisible = true
                emptyHistoryLayout.isVisible = true
                refreshHistory.isVisible = false
                emptyHistoryText.text = resources.getText(R.string.empty_history_text)
            }
        }
        else {
            binding?.apply {
                historyRecyclerView.isVisible = false
                refreshEmptyHistory.isVisible = true
                emptyHistoryLayout.isVisible = true
                refreshHistory.isVisible = false
                emptyHistoryText.text = resources.getText(R.string.required_to_access_premium_feature)
            }
        }

        if(PremiumInterface.isPremium) swipeToRemoveHistory()

        refreshEmptyHistory()

        refreshHistory()
    }

    override fun onResume() {
        super.onResume()
        if(isResume) {
            val historyDB = HistoryDB(requireContext())
            if(PremiumInterface.isPremium && HistoryHelper.getHistoryCount(requireContext()) > 0) {
                binding?.apply {
                    refreshEmptyHistory.isVisible = false
                    emptyHistoryLayout.isVisible = false
                    historyRecyclerView.isVisible = true
                    refreshHistory.isVisible = true
                }
                MainActivity.instance?.toolbar?.menu?.apply {
                    findItem(R.id.history_premium)?.isVisible = false
                    findItem(R.id.clear_history)?.isVisible = true
                }
                if(HistoryHelper.getHistoryCount(requireContext()) == 1L) {
                    historyAdapter = HistoryAdapter(historyDB.readDB())
                    binding?.historyRecyclerView?.apply {
                        setItemViewCacheSize(historyAdapter.itemCount)
                        adapter = historyAdapter
                    }
                }
                else historyAdapter.update(requireContext())

            }
            else if(PremiumInterface.isPremium) {
                binding?.apply {
                    historyRecyclerView.isVisible = false
                    refreshEmptyHistory.isVisible = true
                    emptyHistoryLayout.isVisible = true
                    refreshHistory.isVisible = false
                    emptyHistoryText.text = resources.getText(R.string.empty_history_text)
                }
            }
            else {
                binding?.apply {
                    historyRecyclerView.isVisible = false
                    refreshEmptyHistory.isVisible = true
                    emptyHistoryLayout.isVisible = true
                    refreshHistory.isVisible = false
                    emptyHistoryText.text = resources.getText(R.string.required_to_access_premium_feature)
                }
            }
        }
        else isResume = true
    }

    override fun onDestroy() {
        instance = null
        HistoryAdapter.instance = null
        super.onDestroy()
    }

    private fun swipeToRemoveHistory() =
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                                target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {

                var isRemoving = true

                val position = viewHolder.bindingAdapterPosition

                historyAdapter.remove(requireContext(), position)

                binding?.historyRecyclerView?.setItemViewCacheSize(historyAdapter.itemCount)

                binding?.refreshHistory?.isEnabled = false

                Snackbar.make(binding?.historyRecyclerView!!, getString(R.string.history_removed),
                    Snackbar.LENGTH_LONG).apply {

                    setAction(getString(R.string.undo)) {

                        isRemoving = false

                        historyAdapter.undoRemoving(requireContext(), position)

                        binding?.historyRecyclerView?.setItemViewCacheSize(historyAdapter.itemCount)

                    }

                    show()
                }

                CoroutineScope(Dispatchers.Main).launch {

                    delay(3.seconds)
                    if(isRemoving) {
                        val historyList = HistoryDB(requireContext()).readDB()

                        try {
                            HistoryHelper.remove(requireContext(),
                                historyList[historyList.size - 1 - position].residualCapacity)
                        }

                        catch (_: ArrayIndexOutOfBoundsException) {}

                        finally {
                            binding?.refreshHistory?.isEnabled = true

                            if(HistoryHelper.isHistoryEmpty(requireContext())) emptyHistory()
                        }
                    }
                }

            }

        }).attachToRecyclerView(binding?.historyRecyclerView)

    private fun refreshEmptyHistory() {
        binding?.apply {
            refreshEmptyHistory.apply {
                setColorSchemeColors(ContextCompat.getColor(requireContext(),
                    R.color.swipe_refresh_layout_progress))
                setProgressBackgroundColorSchemeColor(ContextCompat.getColor(requireContext(),
                    R.color.swipe_refresh_layout_progress_background))
                setOnRefreshListener {
                    isRefreshing = true
                    if(PremiumInterface.isPremium &&
                        HistoryHelper.isHistoryNotEmpty(requireContext())) {
                        historyAdapter.update(requireContext())
                        isVisible = false
                        refreshHistory.isVisible = true
                        emptyHistoryLayout.isVisible = false
                        historyRecyclerView.isVisible = true
                        MainActivity.instance?.toolbar?.menu?.apply {
                            findItem(R.id.history_premium)?.isVisible = false
                            findItem(R.id.clear_history)?.isVisible = true
                        }
                    }
                    else if(PremiumInterface.isPremium) {
                        historyRecyclerView.isVisible = false
                        isVisible = true
                        refreshHistory.isVisible = false
                        emptyHistoryLayout.isVisible = true
                        emptyHistoryText.text = resources.getText(R.string.empty_history_text)
                        MainActivity.instance?.clearMenu()
                    }
                    else {
                        historyRecyclerView.isVisible = false
                        isVisible = true
                        refreshHistory.isVisible = false
                        emptyHistoryLayout.isVisible = true
                        emptyHistoryText.text = resources.getText(R.string.required_to_access_premium_feature)
                        MainActivity.instance?.toolbar?.menu?.apply {
                            findItem(R.id.history_premium)?.isVisible = true
                            findItem(R.id.clear_history)?.isVisible = false
                        }
                    }
                    isRefreshing = false
                }
            }
        }
    }

    private fun refreshHistory() {
        binding?.apply {
            binding?.refreshHistory?.apply {
                setColorSchemeColors(ContextCompat.getColor(requireContext(),
                    R.color.swipe_refresh_layout_progress))
                setProgressBackgroundColorSchemeColor(ContextCompat.getColor(requireContext(),
                    R.color.swipe_refresh_layout_progress_background))
                setOnRefreshListener {
                    isRefreshing = true
                    if(PremiumInterface.isPremium &&
                        HistoryHelper.isHistoryNotEmpty(requireContext())) {
                        historyAdapter.update(requireContext())
                        refreshEmptyHistory.isVisible = false
                        isVisible = true
                        emptyHistoryLayout.isVisible = false
                        historyRecyclerView.isVisible = true
                        MainActivity.instance?.toolbar?.menu?.apply {
                            findItem(R.id.history_premium)?.isVisible = false
                            findItem(R.id.clear_history)?.isVisible = true
                        }
                    }
                    else emptyHistory()
                    isRefreshing = false
                }
            }
        }
    }

    fun emptyHistory() {
        MainActivity.instance?.toolbar?.title = getString(R.string.history)
        MainActivity.instance?.toolbar?.menu?.apply {
            findItem(R.id.history_premium)?.isVisible = false
            findItem(R.id.clear_history)?.isVisible = false
        }
        binding?.apply {
            historyRecyclerView.isVisible = false
            refreshHistory.isVisible = false
            refreshEmptyHistory.isVisible = true
            emptyHistoryLayout.isVisible = true
            emptyHistoryText.text = getText(if(PremiumInterface.isPremium)
                R.string.empty_history_text else R.string.required_to_access_premium_feature)
        }
    }

    @Suppress("DEPRECATION")
    private fun isInstalledFromGooglePlay() =
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Constants.GOOGLE_PLAY_PACKAGE_NAME == requireContext().packageManager
                .getInstallSourceInfo(requireContext().packageName).installingPackageName
        else Constants.GOOGLE_PLAY_PACKAGE_NAME == requireContext().packageManager
            .getInstallerPackageName(requireContext().packageName)
}