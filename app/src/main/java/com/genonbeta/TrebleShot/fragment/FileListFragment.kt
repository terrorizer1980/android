/*
 * Copyright (C) 2019 Veli Tasalı
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.genonbeta.TrebleShot.fragment

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import com.genonbeta.TrebleShot.R
import com.genonbeta.TrebleShot.adapter.FileListAdapter
import com.genonbeta.TrebleShot.adapter.FileListAdapter.*
import com.genonbeta.TrebleShot.app.GroupEditableListFragment
import com.genonbeta.TrebleShot.database.Kuick
import com.genonbeta.TrebleShot.ui.callback.SharingPerformerMenuCallback
import com.genonbeta.TrebleShot.util.AppUtils
import com.genonbeta.TrebleShot.widget.EditableListAdapter
import com.genonbeta.TrebleShot.widget.GroupEditableListAdapter
import com.genonbeta.TrebleShot.widget.GroupEditableListAdapter.*
import com.genonbeta.android.database.KuickDb
import com.genonbeta.android.framework.`object`.Selectable
import com.genonbeta.android.framework.io.DocumentFile
import com.genonbeta.android.framework.ui.PerformerMenu
import com.genonbeta.android.framework.util.FileUtils
import com.genonbeta.android.framework.util.actionperformer.PerformerEngineProvider
import com.google.android.material.snackbar.Snackbar
import java.io.FileNotFoundException

abstract class FileListFragment : GroupEditableListFragment<FileHolder, GroupViewHolder, FileListAdapter>() {
    private var mLastKnownPath: DocumentFile? = null
    private var mPathChangedListener: OnPathChangedListener? = null
    private val mIntentFilter = IntentFilter()
    private val mReceiver = object : BroadcastReceiver() {
        private var mUpdateSnackbar: Snackbar? = null
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_FILE_LIST_CHANGED == intent.action && intent.hasExtra(EXTRA_FILE_PARENT)) {
                try {
                    val parentUri: Any? = intent.getParcelableExtra(EXTRA_FILE_PARENT)
                    if (parentUri == null && adapter.getPath() == null) {
                        refreshList()
                    } else if (parentUri != null) {
                        val parentFile = FileUtils.fromUri(getContext(), parentUri as Uri?)
                        if (getAdapter().getPath() != null && parentFile.uri == getAdapter().getPath().getUri())
                            refreshList()
                        else if (intent.hasExtra(EXTRA_FILE_NAME)) {
                            if (mUpdateSnackbar == null) mUpdateSnackbar =
                                createSnackbar(R.string.mesg_newFilesReceived)
                            mUpdateSnackbar?.setText(
                                getString(
                                    R.string.mesg_fileReceived,
                                    intent.getStringExtra(EXTRA_FILE_NAME)
                                )
                            )
                                .setAction(R.string.butn_show) { _: View? -> goPath(parentFile) }
                                .show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if (getAdapter().getPath() == null && KuickDb.ACTION_DATABASE_CHANGE == intent.action) {
                val data: KuickDb.BroadcastData = KuickDb.toData(intent)
                if (Kuick.TABLE_FILEBOOKMARK == data.tableName)
                    refreshList()
            } else if (ACTION_FILE_RENAME_COMPLETED == intent.action)
                refreshList()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFilteringSupported(true)
        setDefaultOrderingCriteria(EditableListAdapter.MODE_SORT_ORDER_ASCENDING)
        setDefaultSortingCriteria(EditableListAdapter.MODE_SORT_BY_NAME)
        setDefaultGroupingCriteria(FileListAdapter.MODE_GROUP_BY_DEFAULT)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setListAdapter(FileListAdapter(this))
        setEmptyListImage(R.drawable.ic_folder_white_24dp)
        setEmptyListText(getString(R.string.text_listEmptyFiles))
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mIntentFilter.addAction(ACTION_FILE_LIST_CHANGED)
        mIntentFilter.addAction(ACTION_FILE_RENAME_COMPLETED)
        mIntentFilter.addAction(KuickDb.ACTION_DATABASE_CHANGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK)
            if (requestCode == REQUEST_WRITE_ACCESS) {
                val pathUri = data!!.data
                if (Build.VERSION.SDK_INT >= 21 && pathUri != null && getContext() != null) {
                    context?.contentResolver?.takePersistableUriPermission(
                        pathUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )

                    try {
                        val kuick = AppUtils.getKuick(getContext())
                        val file = DocumentFile.fromUri(getContext(), pathUri, true)
                        kuick.publish(FileHolder(getContext(), file))
                        kuick.broadcast()
                        goPath(null)
                    } catch (e: FileNotFoundException) {
                        e.printStackTrace()
                        Toast.makeText(getContext(), R.string.mesg_somethingWentWrong, Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.actions_file_list, menu)
        val mountDirectory = menu.findItem(R.id.actions_file_list_mount_directory)
        if (Build.VERSION.SDK_INT >= 21 && mountDirectory != null) mountDirectory.isVisible = true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.actions_file_list_mount_directory) {
            requestMountStorage()
        } else if (id == R.id.actions_file_list_toggle_shortcut && getAdapter().getPath() != null) {
            shortcutItem<FileHolder>(this, FileHolder(getContext(), getAdapter().getPath()))
        } else return super.onOptionsItemSelected(item)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        val shortcutMenuItem = menu.findItem(R.id.actions_file_list_toggle_shortcut)
        if (shortcutMenuItem != null) {
            val hasPath = getAdapter().getPath() != null
            shortcutMenuItem.isEnabled = hasPath
            if (hasPath) try {
                AppUtils.getKuick(getContext())
                    .reconstruct<Any, FileHolder>(FileHolder(getContext(), getAdapter().getPath()))
                shortcutMenuItem.setTitle(R.string.butn_removeShortcut)
            } catch (e: Exception) {
                shortcutMenuItem.setTitle(R.string.butn_addShortcut)
            }
        }
    }

    override fun onCreatePerformerMenu(context: Context?): PerformerMenu? {
        return PerformerMenu(context, SelectionCallback(this, this))
    }

    override fun onResume() {
        super.onResume()
        requireActivity().registerReceiver(mReceiver, mIntentFilter)
    }

    override fun onPause() {
        super.onPause()
        requireActivity().unregisterReceiver(mReceiver)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (getAdapter() != null && getAdapter().getPath() != null) outState.putString(
            EXTRA_FILE_LOCATION,
            getAdapter().getPath().getUri().toString()
        )
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (savedInstanceState != null && savedInstanceState.containsKey(EXTRA_FILE_LOCATION)) {
            try {
                goPath(FileUtils.fromUri(getContext(), Uri.parse(savedInstanceState.getString(EXTRA_FILE_LOCATION))))
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            }
        }
    }

    protected override fun onListRefreshed() {
        super.onListRefreshed()

        // If the current path is different from the older one, move the scroll position
        // to the top.
        val pathOnTrial: DocumentFile = getAdapter().getPath()
        if (!(mLastKnownPath == null && getAdapter().getPath() == null)
            && mLastKnownPath != null && mLastKnownPath != pathOnTrial
        ) getListView().scrollToPosition(0)
        mLastKnownPath = pathOnTrial
    }

    fun goPath(file: DocumentFile?) {
        if (file != null && !file.canRead()) {
            createSnackbar(R.string.mesg_errorReadFolder, file.name)
                .show()
            return
        }
        if (mPathChangedListener != null) mPathChangedListener!!.onPathChanged(file)
        getAdapter().goPath(file)
        refreshList()
    }

    override fun performDefaultLayoutClick(holder: GroupViewHolder, `object`: FileHolder): Boolean {
        if (`object`.getViewType() == GroupEditableListAdapter.VIEW_TYPE_ACTION_BUTTON
            && `object`.getRequestCode() == FileListAdapter.REQUEST_CODE_MOUNT_FOLDER
        ) requestMountStorage() else if (`object`.file != null && `object`.file.isDirectory()) {
            goPath(`object`.file)
            AppUtils.showFolderSelectionHelp<FileHolder>(this)
        } else performLayoutClickOpen(holder, `object`)
        return true
    }

    override fun performLayoutClickOpen(holder: GroupViewHolder, `object`: FileHolder): Boolean {
        return com.genonbeta.TrebleShot.util.FileUtils.openUriForeground(
            getActivity(),
            `object`.file
        ) || super.performLayoutClickOpen(holder, `object`)
    }

    fun requestMountStorage() {
        if (Build.VERSION.SDK_INT < 21) return
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQUEST_WRITE_ACCESS)
        Toast.makeText(getActivity(), R.string.mesg_mountDirectoryHelp, Toast.LENGTH_LONG).show()
    }

    override fun setItemSelected(holder: GroupViewHolder): Boolean {
        when (getAdapterImpl().getItem(holder.getAdapterPosition()).getType()) {
            FileHolder.Type.SaveLocation, FileHolder.Type.Folder -> return false
        }
        return super.setItemSelected(holder)
    }

    fun setOnPathChangedListener(pathChangedListener: OnPathChangedListener?) {
        mPathChangedListener = pathChangedListener
    }

    interface OnPathChangedListener {
        fun onPathChanged(file: DocumentFile?)
    }

    private class SelectionCallback(private val mFragment: FileListFragment, provider: PerformerEngineProvider) :
        SharingPerformerMenuCallback(
            mFragment.activity, provider
        ) {
        override fun onPerformerMenuList(
            performerMenu: PerformerMenu,
            inflater: MenuInflater,
            targetMenu: Menu
        ): Boolean {
            super.onPerformerMenuList(performerMenu, inflater, targetMenu)
            inflater.inflate(R.menu.action_mode_file, targetMenu)
            return true
        }

        override fun onPerformerMenuSelected(performerMenu: PerformerMenu, item: MenuItem): Boolean {
            val performerEngine = performerEngine ?: return false
            val selectableList: List<Selectable> = ArrayList<Selectable>(performerEngine.selectionList)
            val fileList: MutableList<FileHolder> = ArrayList()
            for (selectable in selectableList)
                if (selectable is FileHolder)
                    fileList.add(selectable as FileHolder)
            return if (fileList.size <= 0 || !handleEditingAction(
                    item,
                    mFragment,
                    fileList
                )
            ) super.onPerformerMenuSelected(performerMenu, item) else true
        }
    }

    companion object {
        val TAG = FileListFragment::class.java.simpleName
        const val REQUEST_WRITE_ACCESS = 264
        const val ACTION_FILE_LIST_CHANGED = "com.genonbeta.TrebleShot.action.FILE_LIST_CHANGED"
        const val ACTION_FILE_RENAME_COMPLETED = "com.genonbeta.TrebleShot.action.FILE_RENAME_COMPLETED"
        const val EXTRA_FILE_PARENT = "extraPath"
        const val EXTRA_FILE_NAME = "extraFile"
        const val EXTRA_FILE_LOCATION = "extraFileLocation"
        fun handleEditingAction(
            item: MenuItem, fragment: FileListFragment,
            selectedItemList: List<FileHolder>?
        ): Boolean {
            val adapter: FileListAdapter = fragment.getAdapter()
            val id = item.itemId
            if (id == R.id.action_mode_file_delete) {
                FileDeletionDialog(fragment.getActivity(), selectedItemList).show()
            } else if (id == R.id.action_mode_file_rename) {
                FileRenameDialog(fragment.getActivity(), selectedItemList).show()
            } else if (id == R.id.action_mode_file_copy_here) {
                // FIXME: 8/17/20 Sharing with third-party has problems. This should be fixed.
                /*} else if (id == R.id.action_mode_file_share_all_apps) {
            Intent intent = new Intent(selectedItemList.size() > 1 ? Intent.ACTION_SEND_MULTIPLE : Intent.ACTION_SEND)
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            if (selectedItemList.size() > 1) {
                MIMEGrouper mimeGrouper = new MIMEGrouper();
                ArrayList<Uri> uriList = new ArrayList<>();

                for (FileHolder sharedItem : selectedItemList) {

                    uriList.add();

                    if (!mimeGrouper.isLocked())
                        mimeGrouper.process(sharedItem.mimeType);
                }

                intent.setType(mimeGrouper.toString())
                        .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList);
            } else if (selectedItemList.size() == 1) {
                Shareable sharedItem = selectedItemList.get(0);

                intent.setType(sharedItem.mimeType)
                        .putExtra(Intent.EXTRA_STREAM, sharedItem.uri);
            }

            try {
                fragment.requireActivity().startActivity(Intent.createChooser(intent, fragment.getString(
                        R.string.text_fileShareAppChoose)));
                return true;
            } catch (ActivityNotFoundException e) {
                fragment.createSnackbar(R.string.mesg_noActivityFound, Toast.LENGTH_SHORT).show();
            } catch (Throwable e) {
                e.printStackTrace();
                fragment.createSnackbar(R.string.mesg_somethingWentWrong, Toast.LENGTH_SHORT).show();
            }*/
            } else return false
            return true
        }

        fun <T : Editable?> shortcutItem(fragment: EditableListFragmentBase<T>, holder: FileHolder?) {
            val kuick = AppUtils.getKuick(fragment.context)
            try {
                kuick.reconstruct<Any, FileHolder>(holder)
                kuick.remove(holder)
                fragment.createSnackbar(R.string.mesg_removed).show()
            } catch (e: Exception) {
                kuick.insert<Any, FileHolder>(holder)
                fragment.createSnackbar(R.string.mesg_added).show()
            } finally {
                kuick.broadcast()
            }
        }
    }
}