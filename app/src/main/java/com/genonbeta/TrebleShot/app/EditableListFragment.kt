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
package com.genonbeta.TrebleShot.app

import android.app.Activity
import android.content.*
import android.database.ContentObserver
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.collection.ArrayMap
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import androidx.recyclerview.widget.RecyclerView
import com.genonbeta.TrebleShot.R
import com.genonbeta.TrebleShot.dataobject.Editable
import com.genonbeta.TrebleShot.dataobject.Shareable
import com.genonbeta.TrebleShot.dialog.SelectionEditorDialog
import com.genonbeta.TrebleShot.util.AppUtils
import com.genonbeta.TrebleShot.util.SelectionUtils
import com.genonbeta.TrebleShot.view.LongTextBubbleFastScrollViewProvider
import com.genonbeta.TrebleShot.widget.EditableListAdapter
import com.genonbeta.TrebleShot.widget.recyclerview.ItemOffsetDecoration
import com.genonbeta.TrebleShot.widget.recyclerview.SwipeSelectionListener
import com.genonbeta.TrebleShot.widgetimport.EditableListAdapterBase
import com.genonbeta.android.framework.util.actionperformer.Selectable
import com.genonbeta.android.framework.app.DynamicRecyclerViewFragment
import com.genonbeta.android.framework.ui.PerformerMenu
import com.genonbeta.android.framework.util.Files
import com.genonbeta.android.framework.util.actionperformer.*
import com.genonbeta.android.framework.widget.RecyclerViewAdapter.*
import com.genonbeta.android.framework.widget.recyclerview.FastScroller
import java.util.*

/**
 * created by: Veli
 * date: 21.11.2017 10:12
 */
abstract class EditableListFragment<T : Editable, V : ViewHolder, E : EditableListAdapter<T, V>>
    : DynamicRecyclerViewFragment<T, V, E>(), IEditableListFragment<T, V> {
    override var adapter: E
        get() = super.adapter
        set(value) {
            super.adapter = value
            fastScroller?.recyclerView = listView
            engineConnection.setSelectableProvider(adapter)

            adapter.fragment = this
            adapter.notifyGridSizeUpdate(gridSize, isScreenLarge())
            adapter.setSortingCriteria(sortingCriteria, orderingCriteria)
        }

    override val adapterImpl: EditableListAdapterBase<T>
        get() = adapter

    val defaultContentObserver: ContentObserver
        get() = observer ?: object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun deliverSelfNotifications(): Boolean {
                return true
            }

            override fun onChange(selfChange: Boolean) {
                refreshList()
            }
        }.also { observer = it }

    var defaultItemOffsetPadding = -1f

    var defaultOrderingCriteria: Int = EditableListAdapter.MODE_SORT_ORDER_ASCENDING

    var defaultSortingCriteria: Int = EditableListAdapter.MODE_SORT_BY_NAME

    var defaultViewingGridSize = 1

    var defaultViewingGridSizeLandscape = 1

    var dividerResId = R.id.abstract_layout_fast_scroll_recyclerview_bottom_divider

    override val engineConnection: IEngineConnection<T> = EngineConnection(this, this)

    var fastScroller: FastScroller? = null
        private set

    override var filteringDelegate: FilteringDelegate<T> = object : FilteringDelegate<T> {
        override fun changeFilteringKeyword(keyword: String?): Boolean {
            searchText = keyword
            return true
        }

        override fun getFilteringKeyword(listFragment: EditableListFragmentBase<T>): Array<String>? {
            return searchText?.split(" ".toRegex())?.toTypedArray()
        }
    }

    override var gridSize: Int
        get() {
            return if (isScreenLandscape()) viewPreferences.getInt(
                getUniqueSettingKey("GridSizeLandscape"),
                defaultViewingGridSizeLandscape
            ) else viewPreferences.getInt(
                getUniqueSettingKey("GridSize"),
                defaultViewingGridSize
            )
        }
        set(value) {
            viewPreferences.edit()
                .putInt(getUniqueSettingKey("GridSize" + if (isScreenLandscape()) "Landscape" else ""), value)
                .apply()
            applyViewingChanges(value)
        }

    var hasBottomSpace = false

    var itemOffsetDecorationEnabled = false

    var itemOffsetForEdgesEnabled = true

    override var isFilteringSupported: Boolean = false

    override var isGridSupported: Boolean = false

    override var isLocalSelectionActivated: Boolean = false
        set(value) {
            field = value
            if (value) {
                val selectedItems: MutableList<T> = ArrayList(engineConnection.getSelectedItemList() ?: emptyList())
                if (selectedItems.isNotEmpty()) engineConnection.setSelected(
                    selectedItems,
                    IntArray(selectedItems.size),
                    false
                )
            }
            activity?.invalidateOptionsMenu()
        }

    override var isRefreshRequested: Boolean = false

    override var isSelectByClick: Boolean = false
        get() = field || isUsingLocalSelection

    override var isSortingSupported: Boolean = true

    override var isTwoRowLayout: Boolean = false
        get() = AppUtils.getDefaultPreferences(requireContext()).getBoolean("two_row_layout", true)
        set(value) {
            field = value
            applyViewingChanges(optimumGridSize, true)
        }

    override var isUsingLocalSelection: Boolean
        get() = activity !is PerformerEngineProvider && performerMenu != null
        set(value) {

        }

    var layoutResId = R.layout.abstract_layout_editable_list_fragment

    private var layoutClickListener: LayoutClickListener<V>? = null

    override var listView: RecyclerView
        get() = super.listView
        set(value) {
            super.listView = value

            snackbarContainer = value
            value.addOnItemTouchListener(SwipeSelectionListener(this))

            if (hasBottomSpace) {
                value.clipToPadding = false
                value.setPadding(0, 0, 0, (resources.getDimension(R.dimen.fab_margin) * 12).toInt())
            }
        }

    private var observer: ContentObserver? = null

    private val optimumGridSize: Int
        get() {
            val preferredGridSize = gridSize
            return if (preferredGridSize > 1) preferredGridSize else if (canShowWideView() && isTwoRowLayout) 2 else 1
        }

    override var orderingCriteria: Int
        get() = viewPreferences.getInt(getUniqueSettingKey("SortOrder"), defaultOrderingCriteria)
        set(value) {
            viewPreferences.edit()
                .putInt(getUniqueSettingKey("SortOrder"), value)
                .apply()
            adapter.setSortingCriteria(sortingCriteria, value)
            refreshList()
        }

    private val orderingOptions: MutableMap<String, Int> = ArrayMap()

    private val performerEngine: IPerformerEngine = PerformerEngine()

    private var performerMenu: PerformerMenu? = null

    private var searchText: String? = null

    private val selectableList: MutableList<T> = ArrayList()

    override var sortingCriteria: Int
        get() = viewPreferences.getInt(getUniqueSettingKey("SortBy"), defaultSortingCriteria)
        set(value) {
            viewPreferences.edit()
                .putInt(getUniqueSettingKey("SortBy"), value)
                .apply()
            adapter.setSortingCriteria(value, orderingCriteria)
            refreshList()
        }

    private val sortingOptions: MutableMap<String, Int> = ArrayMap()

    var twoRowLayoutState: Boolean = false

    val viewPreferences: SharedPreferences
        get() = AppUtils.getViewingPreferences(requireContext())

    open fun onCreatePerformerMenu(context: Context): PerformerMenu? {
        return null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        twoRowLayoutState = isTwoRowLayout

        arguments?.let {
            isSelectByClick = it.getBoolean(ARG_SELECT_BY_CLICK, isSelectByClick)
            hasBottomSpace = it.getBoolean(ARG_HAS_BOTTOM_SPACE, hasBottomSpace)
        }

        performerMenu?.setUp(performerEngine)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(layoutResId, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fastScroller = view.findViewById(R.id.abstract_layout_fast_scroll_recyclerview_fastscroll_view)
        fastScroller?.viewProvider = LongTextBubbleFastScrollViewProvider()

        setDividerVisible(true)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setHasOptionsMenu(true)
        if (itemOffsetDecorationEnabled) {
            val padding = if (defaultItemOffsetPadding > -1)
                defaultItemOffsetPadding
            else
                resources.getDimension(R.dimen.padding_list_content_parent_layout)
            val offsetDecoration = ItemOffsetDecoration(padding.toInt(), itemOffsetForEdgesEnabled, isHorizontalOrientation())

            offsetDecoration.prepare(listView)
            listView.addItemDecoration(offsetDecoration)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
        if (twoRowLayoutState != isTwoRowLayout) toggleTwoRowLayout()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        performerMenu = onCreatePerformerMenu(context)
        engineConnection.setDefinitiveTitle(getDistinctiveTitle(context))
        engineConnection.addSelectionListener(this)
        getPerformerEngine()?.ensureSlot(this, engineConnection)
    }

    override fun onDetach() {
        super.onDetach()
        getPerformerEngine()?.removeSlot(engineConnection)
    }

    override fun onSelected(
        engine: IPerformerEngine, owner: IEngineConnection<T>, selectable: T, isSelected: Boolean,
        position: Int,
    ) {
        if (position >= 0) adapter.syncAndNotify(position) else adapter.syncAllAndNotify()
        ensureLocalSelection()
    }

    override fun onSelected(
        engine: IPerformerEngine, owner: IEngineConnection<T>, selectableList: MutableList<T>,
        isSelected: Boolean, positions: IntArray,
    ) {
        adapter.syncAllAndNotify()
        ensureLocalSelection()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        if (isUsingLocalSelection && isLocalSelectionActivated) performerMenu!!.populateMenu(menu) else {
            inflater.inflate(R.menu.actions_abs_editable_list, menu)
            val filterItem = menu.findItem(R.id.actions_abs_editable_filter)
            if (filterItem != null) {
                filterItem.isVisible = isFilteringSupported
                if (isFilteringSupported) {
                    val view = filterItem.actionView
                    if (view is SearchView) {
                        view.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                            override fun onQueryTextSubmit(query: String): Boolean {
                                refreshList()
                                return true
                            }

                            override fun onQueryTextChange(newText: String): Boolean {
                                searchText = newText
                                refreshList()
                                return true
                            }
                        })
                    }
                }
            }
            val gridSizeItem = menu.findItem(R.id.actions_abs_editable_grid_size)
            if (gridSizeItem != null) {
                val gridSizeMenu: Menu = gridSizeItem.subMenu
                for (i in 1 until if (isScreenLandscape()) 7 else 5) gridSizeMenu.add(
                    R.id.actions_abs_editable_group_grid_size, 0, i,
                    resources.getQuantityString(R.plurals.text_gridRow, i, i)
                )
                gridSizeMenu.setGroupCheckable(R.id.actions_abs_editable_group_grid_size, true, true)
            }
            val sortingOptions: MutableMap<String, Int> = ArrayMap()
            onSortingOptions(sortingOptions)
            if (sortingOptions.isNotEmpty()) {
                this.sortingOptions.clear()
                this.sortingOptions.putAll(sortingOptions)
                applyDynamicMenuItems(
                    menu.findItem(R.id.actions_abs_editable_sort_by),
                    R.id.actions_abs_editable_group_sorting, this.sortingOptions
                )
                val orderingOptions: MutableMap<String, Int> = ArrayMap()
                onOrderingOptions(orderingOptions)
                if (orderingOptions.isNotEmpty()) {
                    this.orderingOptions.clear()
                    this.orderingOptions.putAll(orderingOptions)
                    applyDynamicMenuItems(
                        menu.findItem(R.id.actions_abs_editable_order_by),
                        R.id.actions_abs_editable_group_sort_order, this.orderingOptions
                    )
                }
            }
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        if (!isLocalSelectionActivated) {
            menu.findItem(R.id.actions_abs_editable_sort_by).isEnabled = isSortingSupported
            menu.findItem(R.id.actions_abs_editable_multi_select).isVisible = performerMenu != null
            menu.findItem(R.id.actions_abs_editable_grid_size).isVisible = isGridSupported
            menu.findItem(R.id.actions_abs_editable_sort_by)?.also { sortingItem: MenuItem ->
                sortingItem.isVisible = isSortingSupported
                if (sortingItem.isVisible) {
                    checkPreferredDynamicItem(sortingItem, sortingCriteria, sortingOptions)
                    val orderingItem = menu.findItem(R.id.actions_abs_editable_order_by)
                    orderingItem?.let { checkPreferredDynamicItem(it, orderingCriteria, orderingOptions) }
                }
            }
            menu.findItem(R.id.actions_abs_editable_grid_size)?.also { gridSizeItem: MenuItem ->
                val gridRowMenu: Menu = gridSizeItem.subMenu
                val currentRow = gridSize - 1
                if (currentRow < gridRowMenu.size()) gridRowMenu.getItem(currentRow).isChecked = true
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        val groupId = item.groupId

        if (groupId == R.id.actions_abs_editable_group_sorting)
            sortingCriteria = item.order
        else if (groupId == R.id.actions_abs_editable_group_sort_order)
            orderingCriteria = item.order
        else if (groupId == R.id.actions_abs_editable_group_grid_size)
            gridSize = item.order
        else if (id == R.id.actions_abs_editable_multi_select && performerMenu != null && activity != null)
            isLocalSelectionActivated = !isLocalSelectionActivated
        else if (performerMenu?.onMenuItemClick(item) == true)
            isLocalSelectionActivated = false
        else
            super.onOptionsItemSelected(item)

        return true
    }

    open fun onSortingOptions(options: MutableMap<String, Int>) {
        options[getString(R.string.text_sortByName)] = EditableListAdapter.MODE_SORT_BY_NAME
        options[getString(R.string.text_sortByDate)] = EditableListAdapter.MODE_SORT_BY_DATE
        options[getString(R.string.text_sortBySize)] = EditableListAdapter.MODE_SORT_BY_SIZE
    }

    fun onOrderingOptions(options: MutableMap<String, Int>) {
        options[getString(R.string.text_sortOrderAscending)] = EditableListAdapter.MODE_SORT_ORDER_ASCENDING
        options[getString(R.string.text_sortOrderDescending)] = EditableListAdapter.MODE_SORT_ORDER_DESCENDING
    }

    open fun onGridSpanSize(viewType: Int, currentSpanSize: Int): Int {
        return 1
    }

    protected fun applyDynamicMenuItems(mainItem: MenuItem?, transferId: Int, options: Map<String, Int>) {
        if (mainItem == null)
            return
        mainItem.isVisible = true
        val dynamicMenu = mainItem.subMenu

        for (currentKey in options.keys) options[currentKey]?.let { modeId ->
            dynamicMenu.add(transferId, 0, modeId, currentKey)
        }
        dynamicMenu.setGroupCheckable(transferId, true, true)
    }

    override fun applyViewingChanges(gridSize: Int) {
        applyViewingChanges(gridSize, false)
    }

    fun applyViewingChanges(gridSize: Int, override: Boolean) {
        if (!isGridSupported && !override)
            return

        adapter.notifyGridSizeUpdate(gridSize, isScreenLarge())
        listView.layoutManager = getLayoutManager()
        listView.adapter = adapter
        refreshList()
    }

    fun canShowWideView(): Boolean {
        return !isGridSupported && isScreenLarge() && !isHorizontalOrientation()
    }

    fun checkPreferredDynamicItem(dynamicItem: MenuItem?, preferredItemId: Int, options: Map<String, Int>) {
        if (dynamicItem != null) {
            val gridSizeMenu: Menu = dynamicItem.subMenu
            for (title in options.keys) {
                if (options[title] == preferredItemId) {
                    var menuItem: MenuItem
                    var iterator = 0
                    while (gridSizeMenu.getItem(iterator).also { menuItem = it } != null) {
                        if (title == menuItem.title.toString()) {
                            menuItem.isChecked = true
                            return
                        }
                        iterator++
                    }

                    // normally we should not be here
                    return
                }
            }
        }
    }

    protected fun ensureLocalSelection() {
        val shouldBeEnabled = engineConnection.getSelectedItemList()?.let { it.size > 0 } ?: false

        if (isLocalSelectionActivated != shouldBeEnabled) {
            Log.d(TAG, "ensureLocalSelection: Altering local selection state to '$shouldBeEnabled'")
            isLocalSelectionActivated = shouldBeEnabled
        }
    }

    override fun getLayoutManager(): RecyclerView.LayoutManager {
        val layoutManager = generateGridLayoutManager()
        val optimumGridSize = optimumGridSize
        layoutManager.spanCount = optimumGridSize
        layoutManager.spanSizeLookup = object : SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int = adapter.getItemViewType(position).let {
                return if (it == EditableListAdapter.VIEW_TYPE_DEFAULT) 1 else onGridSpanSize(it, optimumGridSize)
            }
        }
        return layoutManager
    }

    override fun getPerformerEngine(): IPerformerEngine? {
        activity?.let {
            if (it is PerformerEngineProvider)
                return it.getPerformerEngine()
        }

        return performerEngine
    }

    override fun getSelectableList(): MutableList<T> {
        return selectableList
    }

    override fun getUniqueSettingKey(setting: String): String {
        return javaClass.simpleName + "_" + setting
    }

    fun invokeClickListener(holder: V, longClick: Boolean): Boolean {
        return layoutClickListener?.onLayoutClick(this, holder, longClick) ?: false
    }

    override fun loadIfRequested(): Boolean {
        val refreshed = isRefreshRequested
        isRefreshRequested = false
        if (refreshed)
            refreshList()
        return refreshed
    }

    override fun openUri(uri: Uri): Boolean {
        return Files.openUri(requireContext(), uri)
    }

    override fun performLayoutClickOpen(holder: V): Boolean {
        val pos = holder.adapterPosition
        return pos != RecyclerView.NO_POSITION && performLayoutClickOpen(holder, adapterImpl.getItem(pos))
    }

    override fun performLayoutClickOpen(holder: V, target: T): Boolean {
        return target is Shareable && target.uri.let { openUri(it) } == true
    }

    override fun performDefaultLayoutLongClick(holder: V, target: T): Boolean {
        return false
    }

    override fun performLayoutClick(holder: V): Boolean {
        val position = holder.adapterPosition
        return if (position == RecyclerView.NO_POSITION) false else setItemSelected(holder)
                || invokeClickListener(holder, false)
                || performDefaultLayoutClick(holder, adapterImpl.getItem(position))
    }

    override fun performLayoutLongClick(holder: V): Boolean {
        val position = holder.adapterPosition
        return if (position == RecyclerView.NO_POSITION) false else invokeClickListener(holder, true)
                || performDefaultLayoutLongClick(holder, adapterImpl.getItem(position))
                || setItemSelected(holder, true)
    }

    override fun registerLayoutViewClicks(holder: V) {
        holder.itemView.setOnClickListener { performLayoutClick(holder) }
        holder.itemView.setOnLongClickListener { performLayoutLongClick(holder) }
    }

    fun setDividerVisible(visible: Boolean) {
        view?.let {
            val divider = it.findViewById<View>(dividerResId)
            divider.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    override fun setItemSelected(holder: V): Boolean {
        return setItemSelected(holder, isSelectByClick)
    }

    override fun setItemSelected(holder: V, force: Boolean): Boolean {
        if (engineConnection.getSelectedItemList()?.let { it.size <= 0 } == true && !force)
            return false
        try {
            engineConnection.setSelected(holder)
            return true
        } catch (e: SelectableNotFoundException) {
            e.printStackTrace()
        } catch (ignored: CouldNotAlterException) {
        }
        return false
    }

    override fun setLayoutClickListener(clickListener: LayoutClickListener<V>?) {
        layoutClickListener = clickListener
    }

    fun toggleTwoRowLayout() {

    }

    interface LayoutClickListener<V : ViewHolder?> {
        fun onLayoutClick(listFragment: EditableListFragmentBase<*>?, holder: V, longClick: Boolean): Boolean
    }

    interface FilteringDelegate<T : Editable> {
        fun changeFilteringKeyword(keyword: String?): Boolean

        fun getFilteringKeyword(listFragment: EditableListFragmentBase<T>): Array<String>?
    }

    open class SelectionCallback(
        val activity: Activity, private val provider: PerformerEngineProvider,
    ) : PerformerMenu.Callback, PerformerEngineProvider {
        private lateinit var previewSelections: MenuItem

        var foregroundConnection: IEngineConnection<*>? = null

        var cancellable = true

        override fun onPerformerMenuList(
            performerMenu: PerformerMenu,
            inflater: MenuInflater,
            targetMenu: Menu,
        ): Boolean {
            inflater.inflate(R.menu.action_mode_abs_editable, targetMenu)

            if (!cancellable)
                targetMenu.findItem(R.id.action_mode_abs_editable_cancel_selection).isVisible = false

            previewSelections = targetMenu.findItem(R.id.action_mode_abs_editable_preview_selections)

            getPerformerEngine()?.let { updateTitle(SelectionUtils.getTotalSize(it)) }
            return true
        }

        override fun onPerformerMenuSelected(performerMenu: PerformerMenu, item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.action_mode_abs_editable_cancel_selection -> setSelectionState(false, false)
                R.id.action_mode_abs_editable_select_all -> setSelectionState(true, true)
                R.id.action_mode_abs_editable_select_none -> setSelectionState(false, true)
                R.id.action_mode_abs_editable_preview_selections -> SelectionEditorDialog(activity, provider).show()
            }

            // Returning false means that the target item did not execute anything other than small changes.
            // It can be said that descendants can interfere with this by making changes and returning true;
            return false
        }

        override fun onPerformerMenuItemSelection(
            performerMenu: PerformerMenu, engine: IPerformerEngine,
            owner: IBaseEngineConnection, selectable: Selectable,
            isSelected: Boolean, position: Int,
        ): Boolean {
            return true
        }

        override fun onPerformerMenuItemSelection(
            performerMenu: PerformerMenu, engine: IPerformerEngine, owner: IBaseEngineConnection,
            selectableList: MutableList<out Selectable>, isSelected: Boolean, positions: IntArray,
        ): Boolean {
            return true
        }

        override fun onPerformerMenuItemSelected(
            performerMenu: PerformerMenu, engine: IPerformerEngine,
            owner: IBaseEngineConnection, selectable: Selectable, isSelected: Boolean,
            position: Int,
        ) {
            updateTitle(SelectionUtils.getTotalSize(engine))
        }

        override fun onPerformerMenuItemSelected(
            performerMenu: PerformerMenu, engine: IPerformerEngine, owner: IBaseEngineConnection,
            selectableList: MutableList<out Selectable>, isSelected: Boolean, positions: IntArray,
        ) {
            updateTitle(SelectionUtils.getTotalSize(engine))
        }

        fun setSelectionState(newState: Boolean, tryForeground: Boolean) {
            val engine = provider.getPerformerEngine()
            val foreground = foregroundConnection
            if (foreground != null && tryForeground) setSelectionState(
                foreground,
                newState
            ) else if (engine != null) for (baseEngineConnection in engine.getConnectionList()) {
                if (baseEngineConnection is IEngineConnection<*>) setSelectionState(
                    baseEngineConnection, newState
                )
            }
        }

        private fun <T : Selectable> setSelectionState(connection: IEngineConnection<T>, newState: Boolean) {
            connection.getAvailableList()?.let {
                if (it.size > 0) {
                    val positions = IntArray(it.size)
                    for (i in positions.indices)
                        positions[i] = i
                    connection.setSelected(it, positions, newState)
                }
            }
        }

        override fun getPerformerEngine(): IPerformerEngine? {
            return provider.getPerformerEngine()
        }

        private fun updateTitle(totalSelections: Int) {
            // For local selections, the menu may be invalidated or may be just created meaning the menu item may not be
            // available yet.
            previewSelections.title = totalSelections.toString()
            previewSelections.isEnabled = totalSelections > 0
        }
    }

    companion object {
        const val ARG_SELECT_BY_CLICK = "argSelectByClick"
        const val ARG_HAS_BOTTOM_SPACE = "argHasBottomSpace"
    }
}