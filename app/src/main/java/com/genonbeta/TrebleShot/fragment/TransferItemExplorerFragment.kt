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

import android.content.*
import com.genonbeta.TrebleShot.R
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.genonbeta.TrebleShot.adapter.PathResolverRecyclerAdapter
import java.io.File

/**
 * created by: veli
 * date: 3/11/19 7:37 PM
 */
class TransferItemExplorerFragment : TransferItemListFragment() {
    private var mPathView: RecyclerView? = null
    private var mPathAdapter: TransferPathResolverRecyclerAdapter? = null
    private var mToggleButton: ExtendedFloatingActionButton? = null
    override fun onAttach(context: Context) {
        super.onAttach(context)
        setHasBottomSpace(true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setLayoutResId(R.layout.layout_transfer_explorer)
        setDividerView(R.id.layout_transfer_explorer_separator)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mToggleButton = view.findViewById(R.id.layout_transfer_explorer_efab)
        mPathView = view.findViewById(R.id.layout_transfer_explorer_recycler)
        mPathAdapter = TransferPathResolverRecyclerAdapter(context)
        val layoutManager = LinearLayoutManager(
            context, RecyclerView.HORIZONTAL,
            false
        )
        layoutManager.setStackFromEnd(true)
        mPathView.setHasFixedSize(true)
        mPathView.setLayoutManager(layoutManager)
        mPathView.setAdapter(mPathAdapter)
        mPathAdapter.setOnClickListener(PathResolverRecyclerAdapter.OnClickListener { holder: PathResolverRecyclerAdapter.Holder<String?> ->
            goPath(
                holder.index.data
            )
        })
        if (activity is TransferDetailActivity) (activity as TransferDetailActivity).showMenus()
    }

    override fun onListRefreshed() {
        super.onListRefreshed()
        val path = adapter.path
        mPathAdapter.goTo(adapter.member, path?.split(File.separator.toRegex())?.toTypedArray())
        mPathAdapter.notifyDataSetChanged()
        if (mPathAdapter.getItemCount() > 0) mPathView!!.smoothScrollToPosition(mPathAdapter.getItemCount() - 1)
    }

    override fun getDistinctiveTitle(context: Context): CharSequence {
        return context.getString(R.string.text_files)
    }

    fun getToggleButton(): ExtendedFloatingActionButton? {
        return mToggleButton
    }
}