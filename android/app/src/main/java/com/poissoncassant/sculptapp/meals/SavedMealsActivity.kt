package com.poissoncassant.sculptapp.meals

import android.graphics.Color
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.poissoncassant.sculptapp.R
import com.poissoncassant.sculptapp.widget.CalorieGradient
import com.poissoncassant.sculptapp.widget.CalorieWidgetRenderer
import com.poissoncassant.sculptapp.widget.WidgetStateRepository
import java.util.UUID
import kotlin.math.roundToInt

class SavedMealsActivity : ComponentActivity() {
  private lateinit var repository: WidgetStateRepository
  private lateinit var saveButton: Button
  private val customFields = mutableListOf<EditText>()
  private val selectedIds = mutableSetOf<String>()
  private var searchField: EditText? = null
  private var submitted = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    repository = WidgetStateRepository(this)
    val showHistory = intent.action == ACTION_HISTORY
    val widgetState = repository.readState()
    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      isFocusableInTouchMode = true
      background = ShapeDrawable(RectShape()).apply {
        shaderFactory = object : ShapeDrawable.ShaderFactory() {
          override fun resize(width: Int, height: Int) =
              CalorieGradient.createShader(widgetState, width, height)
        }
      }
    }
    val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    saveButton = Button(this).apply {
      setText(if (showHistory) R.string.saved_meals_log_selected else R.string.saved_meals_save)
      setBackgroundResource(R.drawable.widget_button_background)
      minimumHeight = dp(48)
    }
    root.addView(TextView(this).apply {
      setText(if (showHistory) R.string.widget_history else R.string.widget_custom_meal)
      setTextColor(Color.WHITE)
      textSize = 24f
      setPadding(0, 0, 0, dp(8))
    })
    root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
    root.addView(LinearLayout(this).apply {
      gravity = android.view.Gravity.CENTER_VERTICAL
      setPadding(0, dp(8), 0, 0)
      addView(ImageButton(this@SavedMealsActivity).apply {
        setImageResource(R.drawable.ic_capture_back)
        setBackgroundResource(R.drawable.capture_control_background)
        contentDescription = getString(R.string.meal_capture_back)
        setOnClickListener { finish() }
      }, LinearLayout.LayoutParams(dp(48), dp(48)))
      addView(saveButton, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
    })

    if (showHistory) buildHistory(body, savedInstanceState) else buildCustomMeal(body, savedInstanceState)

    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
      val safeArea = insets.getInsets(
          WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or
              WindowInsetsCompat.Type.ime(),
      )
      view.setPadding(safeArea.left + dp(16), safeArea.top + dp(12),
          safeArea.right + dp(16), safeArea.bottom + dp(12))
      insets
    }
    setContentView(root)
    root.requestFocus()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putStringArrayList(STATE_FIELDS, ArrayList(customFields.map { it.text.toString() }))
    outState.putStringArrayList(STATE_SELECTED, ArrayList(selectedIds))
    outState.putString(STATE_SEARCH, searchField?.text?.toString())
  }

  private fun buildCustomMeal(body: LinearLayout, state: Bundle?) {
    val fields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    body.addView(ScrollView(this).apply { addView(fields) }, LinearLayout.LayoutParams(-1, -1))
    val labels = listOf(
        R.string.saved_meals_name, R.string.saved_meals_calories, R.string.saved_meals_protein,
        R.string.saved_meals_carbs, R.string.saved_meals_fat,
    )
    val restored = state?.getStringArrayList(STATE_FIELDS)
    labels.forEachIndexed { index, label ->
      fields.addView(TextView(this).apply {
        setText(label)
        setTextColor(Color.WHITE)
        setPadding(0, dp(8), 0, 0)
      })
      val field = EditText(this).apply {
        contentDescription = getString(label)
        isSingleLine = true
        inputType = if (index == 0) {
          InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        } else {
          InputType.TYPE_CLASS_NUMBER
        }
        filters = arrayOf(InputFilter.LengthFilter(if (index == 0) 120 else 10))
        setText(restored?.getOrNull(index).orEmpty())
      }
      customFields.add(field)
      fields.addView(field, LinearLayout.LayoutParams(-1, -2))
    }
    saveButton.setOnClickListener {
      if (submitted) return@setOnClickListener
      val name = customFields.first().text.toString().trim()
      if (name.isEmpty()) {
        customFields.first().error = getString(R.string.saved_meals_required)
        return@setOnClickListener
      }
      val values = customFields.drop(1).map { field ->
        val value = field.text.toString().toIntOrNull()
        if (value == null || value < 0) {
          field.error = getString(R.string.saved_meals_invalid_number)
          return@setOnClickListener
        }
        value
      }
      val (calories, protein, carbs, fat) = values
      complete(repository.saveAndLogCustomMeal(
          SavedMeal(UUID.randomUUID().toString(), name, calories, protein, carbs, fat),
      ))
    }
  }

  private fun buildHistory(body: LinearLayout, state: Bundle?) {
    val meals = repository.readSavedMeals().asReversed()
    selectedIds.addAll(state?.getStringArrayList(STATE_SELECTED).orEmpty())
    selectedIds.retainAll(meals.map { it.id }.toSet())
    val search = EditText(this).apply {
      setHint(R.string.saved_meals_search)
      isSingleLine = true
      setText(state?.getString(STATE_SEARCH).orEmpty())
    }
    searchField = search
    body.addView(search, LinearLayout.LayoutParams(-1, -2))
    val empty = TextView(this).apply {
      setTextColor(Color.WHITE)
      setPadding(0, dp(12), 0, 0)
      setText(if (meals.isEmpty()) R.string.saved_meals_empty else R.string.saved_meals_no_results)
    }
    body.addView(empty)
    val list = ListView(this).apply { choiceMode = ListView.CHOICE_MODE_MULTIPLE }
    body.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))

    var visibleMeals = emptyList<SavedMeal>()
    fun filter(query: String) {
      visibleMeals = meals.filter { it.name.contains(query.trim(), ignoreCase = true) }
      val rows = visibleMeals.map { meal ->
        getString(R.string.saved_meals_row, meal.name, meal.calories,
            meal.proteinGrams, meal.carbsGrams, meal.fatGrams)
      }
      list.clearChoices()
      list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, rows)
      visibleMeals.forEachIndexed { index, meal -> list.setItemChecked(index, meal.id in selectedIds) }
      empty.visibility = if (visibleMeals.isEmpty()) View.VISIBLE else View.GONE
    }

    fun updateSaveButton() {
      saveButton.isEnabled = selectedIds.isNotEmpty()
      saveButton.alpha = if (saveButton.isEnabled) 1f else 0.5f
      saveButton.text = if (selectedIds.isEmpty()) {
        getString(R.string.saved_meals_log_selected)
      } else {
        getString(R.string.saved_meals_log_count, selectedIds.size)
      }
    }

    list.setOnItemClickListener { _, _, position, _ ->
      val id = visibleMeals[position].id
      if (list.isItemChecked(position)) selectedIds.add(id) else selectedIds.remove(id)
      updateSaveButton()
    }
    search.doAfterTextChanged { filter(it.toString()) }
    filter(search.text.toString())
    updateSaveButton()
    saveButton.setOnClickListener {
      if (submitted || selectedIds.isEmpty()) return@setOnClickListener
      val count = selectedIds.size
      val mealName = resources.getQuantityString(R.plurals.saved_meals_count, count, count)
      complete(repository.logSavedSelection(selectedIds, mealName))
    }
  }

  private fun complete(success: Boolean) {
    if (!success) {
      Toast.makeText(this, R.string.saved_meals_log_error, Toast.LENGTH_LONG).show()
      return
    }
    submitted = true
    saveButton.isEnabled = false
    CalorieWidgetRenderer.refreshAll(this, usePartialUpdate = false)
    finish()
  }

  private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

  companion object {
    const val ACTION_HISTORY = "com.poissoncassant.sculptapp.action.MEAL_HISTORY"
    private const val STATE_FIELDS = "fields"
    private const val STATE_SEARCH = "search"
    private const val STATE_SELECTED = "selected"
  }
}
