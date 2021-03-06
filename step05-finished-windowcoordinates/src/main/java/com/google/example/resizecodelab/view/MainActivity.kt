/*      Copyright 2018 Google LLC

        Licensed under the Apache License, Version 2.0 (the "License");
        you may not use this file except in compliance with the License.
        You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

        Unless required by applicable law or agreed to in writing, software
        distributed under the License is distributed on an "AS IS" BASIS,
        WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        See the License for the specific language governing permissions and
        limitations under the License.
*/
package com.google.example.resizecodelab.view

import android.content.res.Configuration
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.Gravity
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.animation.AnticipateOvershootInterpolator
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintsChangedListener
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.example.resizecodelab.R
import com.google.example.resizecodelab.model.AppData
import com.google.example.resizecodelab.model.Suggestion
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main_shell.*


class MainActivity : AppCompatActivity() {

    companion object {
        private const val KEY_PRODUCT_NAME = "KEY_PRODUCT_NAME"
        private const val KEY_EXPANDED = "KEY_EXPANDED"
    }

    private lateinit var reviewAdapter: ReviewAdapter
    private lateinit var suggestionAdapter: SuggestionAdapter
    private lateinit var viewModel : MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_shell)

        //Set up constraint layout animations
        constraintMain.loadLayoutDescription(R.xml.constraint_states)
        constraintMain.setOnConstraintsChanged(object : ConstraintsChangedListener() {

            override fun preLayoutChange(state: Int, layoutId: Int) {
                //Layout files are no longer replaced so we manually propogate changes
                progressLoadingReviews.visibility = if (viewModel.appData.value == null) VISIBLE else INVISIBLE

                val changeBounds = ChangeBounds()
                changeBounds.duration = 600
                changeBounds.interpolator = AnticipateOvershootInterpolator(0.2f)

                TransitionManager.beginDelayedTransition(constraintMain, changeBounds)

                when (layoutId) {
                    R.layout.activity_main -> {
                        val reviewLayoutManager = LinearLayoutManager(
                            baseContext,
                            RecyclerView.VERTICAL,
                            false
                        )
                        recyclerReviews.layoutManager = reviewLayoutManager

                        val suggestionLayoutManager = LinearLayoutManager(
                            baseContext,
                            LinearLayoutManager.HORIZONTAL,
                            false
                        )
                        recyclerSuggested.layoutManager = suggestionLayoutManager
                    }

                    R.layout.activity_main_land -> {
                        val reviewLayoutManager = GridLayoutManager(baseContext, 2)
                        recyclerReviews.layoutManager = reviewLayoutManager

                        val suggestionLayoutManager = LinearLayoutManager(
                            baseContext,
                            LinearLayoutManager.HORIZONTAL,
                            false
                        )
                        recyclerSuggested.layoutManager = suggestionLayoutManager
                    }

                    R.layout.activity_main_w400 -> {
                        val reviewLayoutManager = LinearLayoutManager(
                            baseContext,
                            RecyclerView.VERTICAL,
                            false
                        )
                        recyclerReviews.layoutManager = reviewLayoutManager

                        val suggestionLayoutManager = GridLayoutManager(baseContext, 2)
                        recyclerSuggested.layoutManager = suggestionLayoutManager
                    }

                    R.layout.activity_main_w600_land -> {
                        val reviewLayoutManager = GridLayoutManager(baseContext, 2)
                        recyclerReviews.layoutManager = reviewLayoutManager

                        val suggestionLayoutManager = GridLayoutManager(baseContext, 3)
                        recyclerSuggested.layoutManager = suggestionLayoutManager
                    }
                }
            }

            override fun postLayoutChange(stateId: Int, layoutId: Int) {
                //Request all layout elements be redrawn
                constraintMain.requestLayout()
            }
        })

        //Retrieve the ViewModel with state data
        viewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)

        //Restore savedInstanceState variables
        savedInstanceState?.getString(KEY_PRODUCT_NAME)?.let { viewModel.setProductName(it) }
        savedInstanceState?.getBoolean(KEY_EXPANDED)?.let { viewModel.setDescriptionExpanded(it) }

        //Set up recycler view for reviews
        reviewAdapter = ReviewAdapter()
        recyclerReviews.apply {
            adapter = reviewAdapter
        }

        //Set up recycler view for suggested products
        suggestionAdapter = SuggestionAdapter()
        recyclerSuggested.apply {
            adapter = suggestionAdapter
        }
        suggestionAdapter.updateSuggestions(getSuggestedProducts())

        //Expand/collapse button for product description
        buttonExpand.setOnClickListener { _ ->
            viewModel.appData.value?.let {
                toggleExpandButton();
            }
        }

        //Add Observer to review data
        viewModel.appData.observe(this, Observer<AppData> { appData ->
            handleReviewsUpdate(appData)
        })

        //Add Observer to the description expand/collapse button
        viewModel.isDescriptionExpanded.observe(this, Observer<Boolean> {
            if (true == it)
                buttonExpand.text = getString(R.string.button_collapse)
            else
                buttonExpand.text = getString(R.string.button_expand)

            textProductDescription.text = getDescriptionText(viewModel.appData.value)
        })

        buttonPurchase.setOnClickListener(View.OnClickListener {
            val textPopupMessage = TextView(this)
            textPopupMessage.gravity = Gravity.CENTER
            textPopupMessage.text = getString(R.string.popup_purchase)
            TextViewCompat.setTextAppearance(textPopupMessage, R.style.TextAppearance_AppCompat_Title)

            val layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER)
            val framePopup = FrameLayout(this)
            framePopup.layoutParams = layoutParams
            framePopup.setBackgroundColor(ContextCompat.getColor(this, R.color.background_floating_material_light))

            framePopup.addView(textPopupMessage)

            //Get window size
            val displayMetrics = resources.displayMetrics
            val screenWidthPx = displayMetrics.widthPixels
            val screenHeightPx = displayMetrics.heightPixels

            //Popup should be 50% of window size
            val popupWidthPx = screenWidthPx / 2
            val popupHeightPx = screenHeightPx / 2

            //Place it in the middle of the window
            val popupX = (screenWidthPx / 2) - (popupWidthPx / 2)
            val popupY = (screenHeightPx / 2) - (popupHeightPx / 2)

            //Show the window
            val popupWindow = PopupWindow(framePopup, popupWidthPx, popupHeightPx, true)
            popupWindow.elevation = 10f
            popupWindow.showAtLocation(scrollMain, Gravity.NO_GRAVITY, popupX, popupY)
        })

        //On first load, make sure we are showing the correct layout
        configurationUpdate(resources.configuration)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_EXPANDED, viewModel.isDescriptionExpanded.value == true)
        outState.putString(KEY_PRODUCT_NAME, viewModel.productName.value)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configurationUpdate(newConfig)
    }

    private fun configurationUpdate(configuration: Configuration) {
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
            constraintMain.setState(R.id.constraintStateLandscape, configuration.screenWidthDp, configuration.screenHeightDp)
        else
            constraintMain.setState(R.id.constraintStatePortrait, configuration.screenWidthDp, configuration.screenHeightDp)
    }

    private fun handleReviewsUpdate(appData: AppData?) {
        progressLoadingReviews.visibility = if (appData == null) VISIBLE else INVISIBLE
        buttonPurchase.visibility = if (appData != null) VISIBLE else INVISIBLE
        buttonExpand.visibility = if (appData != null) VISIBLE else INVISIBLE
        appData?.let {
            textProductName.text = it.title
            textProductCompany.text = it.developer
            textProductDescription.text = getDescriptionText(it)
            reviewAdapter.onReviewsLoaded(it.reviews)
        }
    }

    private fun getDescriptionText(appData: AppData?): String {
        if (null != appData)
            return if (viewModel.isDescriptionExpanded.value == true) appData.description else appData.shortDescription
        else
            return ""
    }

    private fun getSuggestedProducts() : Array<Suggestion> {
        return arrayOf(Suggestion("Gregarious Grogglestock", R.drawable.gregarious),
            Suggestion("Byzantium Barnacles", R.drawable.byzantium),
            Suggestion("Cratankerous Cribblewelps", R.drawable.cratankerous),
            Suggestion("Sunsaritous", R.drawable.sunsari),
            Suggestion("Squiggalia Scrumptiae", R.drawable.squiggle),
            Suggestion("Tenachaterous Torna", R.drawable.tenacious),
            Suggestion("Venemial Venorae", R.drawable.venemial))
    }

    private fun toggleExpandButton() {
        //Invert isDescriptionExpanded
        viewModel.setDescriptionExpanded(viewModel.isDescriptionExpanded.value == false)
    }
}
