package com.poissoncassant.sculptapp.meals

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
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
  private val editMealLauncher =
      registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) recreate()
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    repository = WidgetStateRepository(this)
    val showHistory = intent.action == ACTION_HISTORY
    val editMealId = intent.getStringExtra(EXTRA_EDIT_MEAL_ID)
    val mealToEdit = editMealId?.let { id -> repository.readSavedMeals().firstOrNull { it.id == id } }
    if (editMealId != null && mealToEdit == null) {
      finishEditing(success = false)
      return
    }

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
      setText(when {
        showHistory -> R.string.saved_meals_log_selected
        mealToEdit != null -> R.string.saved_meals_save_changes
        else -> R.string.saved_meals_save
      })
      setBackgroundResource(R.drawable.widget_button_background)
      minimumHeight = dp(48)
    }
    root.addView(TextView(this).apply {
      setText(when {
        showHistory -> R.string.widget_history
        mealToEdit != null -> R.string.saved_meals_edit
        else -> R.string.widget_custom_meal
      })
      setTextColor(Color.WHITE)
      textSize = 24f
      setTypeface(typeface, Typeface.BOLD)
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

    if (showHistory) {
      buildHistory(body, savedInstanceState)
    } else {
      buildCustomMeal(body, savedInstanceState, mealToEdit)
    }

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

  private fun buildCustomMeal(body: LinearLayout, state: Bundle?, mealToEdit: SavedMeal?) {
    val fields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    body.addView(ScrollView(this).apply { addView(fields) }, LinearLayout.LayoutParams(-1, -1))
    val labels = listOf(
        R.string.saved_meals_name, R.string.saved_meals_calories, R.string.saved_meals_protein,
        R.string.saved_meals_carbs, R.string.saved_meals_fat,
    )
    val restored = state?.getStringArrayList(STATE_FIELDS) ?: mealToEdit?.let { meal ->
      listOf(
          meal.name, meal.calories.toString(), meal.proteinGrams.toString(),
          meal.carbsGrams.toString(), meal.fatGrams.toString(),
      )
    }
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
      val meal = SavedMeal(
          mealToEdit?.id ?: UUID.randomUUID().toString(), name, calories, protein, carbs, fat,
      )
      if (mealToEdit == null) {
        complete(repository.saveAndLogCustomMeal(meal))
      } else {
        finishEditing(repository.updateSavedMeal(meal))
      }
    }

    if (mealToEdit != null) {
      fields.addView(Button(this).apply {
        setText(R.string.saved_meals_delete)
        setBackgroundResource(R.drawable.widget_button_background)
        setOnClickListener {
          if (submitted) return@setOnClickListener
          AlertDialog.Builder(this@SavedMealsActivity)
              .setTitle(R.string.saved_meals_delete)
              .setMessage(getString(R.string.saved_meals_confirm_delete, mealToEdit.name))
              .setNegativeButton(android.R.string.cancel, null)
              .setPositiveButton(R.string.saved_meals_delete_action) { _, _ ->
                finishEditing(repository.deleteSavedMeal(mealToEdit.id))
              }
              .show()
        }
      }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
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
    val list = ListView(this).apply { itemsCanFocus = true }
    body.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
    val selectionSummary = layoutInflater.inflate(R.layout.saved_meals_selection_summary, body, false)
    val remainingValue = selectionSummary.findViewById<TextView>(R.id.saved_meals_remaining_value)
    val addedValue = selectionSummary.findViewById<TextView>(R.id.saved_meals_added_value)
    val addedMacros = selectionSummary.findViewById<TextView>(R.id.saved_meals_added_macros)
    body.addView(selectionSummary)

    fun updateSelection() {
      val hasSelection = selectedIds.isNotEmpty()
      saveButton.isEnabled = hasSelection
      saveButton.alpha = if (hasSelection) 1f else 0.5f
      saveButton.text = if (hasSelection) {
        getString(R.string.saved_meals_log_count, selectedIds.size)
      } else {
        getString(R.string.saved_meals_log_selected)
      }
      selectionSummary.visibility = if (hasSelection) View.VISIBLE else View.GONE
      if (!hasSelection) return

      val selectedMeals = meals.filter { it.id in selectedIds }
      val calories = selectedMeals.sumOf { it.calories.toLong() }
      val remainingAfter = repository.readState().caloriesRemaining.toLong() - calories
      remainingValue.text = getString(R.string.widget_remaining_format, remainingAfter)
      addedValue.text = getString(R.string.saved_meals_calories_to_add, calories)
      addedMacros.text = getString(
          R.string.saved_meals_macros_to_add,
          selectedMeals.sumOf { it.proteinGrams.toLong() },
          selectedMeals.sumOf { it.carbsGrams.toLong() },
          selectedMeals.sumOf { it.fatGrams.toLong() },
      )
    }

    fun filter(query: String) {
      val visibleMeals = meals.filter { it.name.contains(query.trim(), ignoreCase = true) }
      list.adapter = object : ArrayAdapter<SavedMeal>(
          this@SavedMealsActivity, R.layout.saved_meal_history_row, visibleMeals,
      ) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
          val meal = visibleMeals[position]
          val row = convertView ?: layoutInflater.inflate(R.layout.saved_meal_history_row, parent, false)
          val label = row.findViewById<TextView>(R.id.saved_meal_row_label)
          val selected = row.findViewById<CheckBox>(R.id.saved_meal_row_selected)
          val edit = row.findViewById<ImageButton>(R.id.saved_meal_row_edit)

          label.text = getString(R.string.saved_meals_row, meal.name, meal.calories,
              meal.proteinGrams, meal.carbsGrams, meal.fatGrams)
          selected.contentDescription = getString(R.string.saved_meals_select_named, meal.name)
          selected.isChecked = meal.id in selectedIds
          selected.setOnClickListener {
            if (selected.isChecked) selectedIds.add(meal.id) else selectedIds.remove(meal.id)
            updateSelection()
          }
          row.setOnClickListener { selected.performClick() }
          edit.contentDescription = getString(R.string.saved_meals_edit_named, meal.name)
          edit.setOnClickListener {
            editMealLauncher.launch(
                Intent(this@SavedMealsActivity, SavedMealsActivity::class.java)
                    .putExtra(EXTRA_EDIT_MEAL_ID, meal.id),
            )
          }
          return row
        }
      }
      empty.visibility = if (visibleMeals.isEmpty()) View.VISIBLE else View.GONE
    }

    search.doAfterTextChanged { filter(it.toString()) }
    filter(search.text.toString())
    updateSelection()
    saveButton.setOnClickListener {
      if (submitted || selectedIds.isEmpty()) return@setOnClickListener
      val count = selectedIds.size
      val mealName = resources.getQuantityString(R.plurals.saved_meals_count, count, count)
      complete(repository.logSavedSelection(selectedIds, mealName))
    }
  }

  private fun finishEditing(success: Boolean) {
    if (!success) {
      Toast.makeText(this, R.string.saved_meals_entry_missing, Toast.LENGTH_LONG).show()
    }
    submitted = true
    setResult(RESULT_OK)
    finish()
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
    private const val EXTRA_EDIT_MEAL_ID = "editMealId"
    private const val STATE_FIELDS = "fields"
    private const val STATE_SEARCH = "search"
    private const val STATE_SELECTED = "selected"
  }
}
