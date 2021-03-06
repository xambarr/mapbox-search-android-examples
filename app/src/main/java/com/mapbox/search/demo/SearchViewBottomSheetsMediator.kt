package com.mapbox.search.demo

import android.os.Bundle
import android.os.Parcelable
import com.mapbox.search.ui.view.SearchBottomSheetView
import com.mapbox.search.ui.view.category.Category
import com.mapbox.search.ui.view.category.SearchCategoriesBottomSheetView
import com.mapbox.search.ui.view.place.SearchPlace
import com.mapbox.search.ui.view.place.SearchPlaceBottomSheetView
import kotlinx.android.parcel.Parcelize
import java.util.LinkedList
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Sample implementation of search cards navigation and coordination.
 */
class SearchViewBottomSheetsMediator(
    private val searchBottomSheetView: SearchBottomSheetView,
    private val placeBottomSheetView: SearchPlaceBottomSheetView,
    private val categoriesBottomSheetView: SearchCategoriesBottomSheetView
) {

    // Stack top points to currently open screen, if empty -> SearchBottomSheetView is open
    private val screensStack = LinkedList<Transaction>()

    private val eventsListeners = CopyOnWriteArrayList<SearchBottomSheetsEventsListener>()

    init {
        with(searchBottomSheetView) {
            addOnCategoryClickListener { openCategory(it) }
            addOnSearchResultClickListener { searchResult ->
                val coordinate = searchResult.coordinate
                if (coordinate != null) {
                    openPlaceCard(SearchPlace.createFromSearchResult(searchResult, coordinate))
                }
            }
            addOnFavoriteClickListener { openPlaceCard(SearchPlace.createFromUserFavorite(it)) }
        }

        with(placeBottomSheetView) {
            addOnBottomSheetStateChangedListener { newState, fromUser ->
                if (newState == SearchPlaceBottomSheetView.BottomSheetState.HIDDEN) {
                    onSubCardHidden(fromUser)
                }
            }
            addOnCloseClickListener { resetToRoot() }
        }

        with(categoriesBottomSheetView) {
            addOnBottomSheetStateChangedListener { newState, fromUser ->
                if (newState == SearchCategoriesBottomSheetView.BottomSheetState.HIDDEN) {
                    onSubCardHidden(fromUser)
                }
            }

            addOnCloseClickListener { resetToRoot() }
            addOnSearchResultClickListener {
                val coordinate = it.coordinate
                if (coordinate != null) {
                    openPlaceCard(SearchPlace.createFromSearchResult(it, coordinate))
                }
            }
        }
    }

    fun onRestoreInstanceState(savedInstanceState: Bundle) {
        val savedStack = savedInstanceState.getParcelableArrayList<Transaction>(KEY_STATE_EXTERNAL_BACK_STACK) ?: return
        screensStack.clear()
        screensStack.addAll(savedStack)
        applyTopState()
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelableArrayList(KEY_STATE_EXTERNAL_BACK_STACK, ArrayList(screensStack))
    }

    private fun onSubCardHidden(hiddenByUser: Boolean) {
        if (hiddenByUser) {
            resetToRoot()
        } else if (categoriesBottomSheetView.isHidden() && placeBottomSheetView.isHidden() && searchBottomSheetView.isHidden()) {
            searchBottomSheetView.restorePreviousNonHiddenState()
            eventsListeners.forEach { it.onBackToMainBottomSheet() }
        }
    }

    private fun openCategory(category: Category, fromBackStack: Boolean = false) {
        if (fromBackStack) {
            categoriesBottomSheetView.restorePreviousNonHiddenState(category)
        } else {
            screensStack.push(Transaction(Screen.CATEGORIES, wrapCategory(category)))
            categoriesBottomSheetView.open(category)
        }
        searchBottomSheetView.hide()
        placeBottomSheetView.hide()
        eventsListeners.forEach { it.onOpenCategoriesBottomSheet(category) }
    }

    private fun openPlaceCard(place: SearchPlace, fromBackStack: Boolean = false) {
        if (!fromBackStack) {
            screensStack.push(Transaction(Screen.PLACE, place))
        }
        placeBottomSheetView.open(place)
        searchBottomSheetView.hide()
        categoriesBottomSheetView.hide()
        eventsListeners.forEach { it.onOpenPlaceBottomSheet(place) }
    }

    private fun resetToRoot() {
        searchBottomSheetView.open()
        placeBottomSheetView.hide()
        categoriesBottomSheetView.hideCardAndCancelLoading()
        screensStack.clear()
        eventsListeners.forEach { it.onBackToMainBottomSheet() }
    }

    private fun popBackStack(): Boolean {
        if (screensStack.isEmpty()) {
            return false
        }
        screensStack.pop()
        applyTopState()
        return true
    }

    private fun applyTopState() {
        if (screensStack.isEmpty()) {
            placeBottomSheetView.hide()
            categoriesBottomSheetView.hideCardAndCancelLoading()
        } else {
            val transaction = screensStack.peek()
            if (transaction == null) {
                fallback { "Transaction is null" }
            } else {
                when (transaction.screen) {
                    Screen.CATEGORIES -> {
                        val category = (transaction.arg as? Bundle)?.unwrapCategory()
                        if (category == null) {
                            fallback { "Saved category is null" }
                        } else {
                            openCategory(category, fromBackStack = true)
                        }
                    }
                    Screen.PLACE -> {
                        val place = transaction.arg as? SearchPlace
                        if (place == null) {
                            fallback { "Saved place is null" }
                        } else {
                            openPlaceCard(place, fromBackStack = true)
                        }
                    }
                }
            }
        }
    }

    fun handleOnBackPressed(): Boolean {
        return searchBottomSheetView.handleOnBackPressed() ||
                categoriesBottomSheetView.handleOnBackPressed() ||
                popBackStack()
    }

    private fun fallback(assertMessage: () -> String) {
        if (BuildConfig.DEBUG) {
            throw IllegalStateException(assertMessage())
        }
        resetToRoot()
    }

    fun addSearchBottomSheetsEventsListener(listener: SearchBottomSheetsEventsListener) {
        eventsListeners.add(listener)
    }

    fun removeSearchBottomSheetsEventsListener(listener: SearchBottomSheetsEventsListener) {
        eventsListeners.remove(listener)
    }

    interface SearchBottomSheetsEventsListener {
        fun onOpenPlaceBottomSheet(place: SearchPlace)
        fun onOpenCategoriesBottomSheet(category: Category)
        fun onBackToMainBottomSheet()
    }

    private enum class Screen {
        CATEGORIES,
        PLACE
    }

    @Parcelize
    private data class Transaction(val screen: Screen, val arg: Parcelable?) : Parcelable

    private companion object {

        const val KEY_STATE_EXTERNAL_BACK_STACK = "SearchViewBottomSheetsMediator.state.external.back_stack"

        const val KEY_CATEGORY = "SearchViewBottomSheetsMediator.key.category"

        fun wrapCategory(category: Category): Bundle {
            return Bundle().apply {
                putSerializable(KEY_CATEGORY, category)
            }
        }

        fun Bundle?.unwrapCategory(): Category? {
            return this?.getSerializable(KEY_CATEGORY) as? Category
        }

        fun SearchCategoriesBottomSheetView.hideCardAndCancelLoading() {
            hide()
            cancelCategoryLoading()
        }
    }
}
