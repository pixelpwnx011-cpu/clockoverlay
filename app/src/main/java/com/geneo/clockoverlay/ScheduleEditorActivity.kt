package com.geneo.clockoverlay

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.geneo.clockoverlay.databinding.ActivityScheduleEditorBinding

/**
 * Full-screen editor for the daily schedule: Period 1-8, Diary Checking, Extra
 * Class -- each with an editable start/end time (4-digit 24-hour, no colon, e.g.
 * "0835"). Same schedule applies every day. Reads/writes through Prefs.getSchedule
 * / saveSchedule, which ClockOverlayService's popup logic also reads from.
 */
class ScheduleEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScheduleEditorBinding

    private data class RowRefs(val rowView: View, val label: String, val etStart: EditText, val etEnd: EditText)
    private val rows = mutableListOf<RowRefs>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSaveSchedule.setOnClickListener { saveSchedule() }
        binding.btnResetDefault.setOnClickListener {
            Prefs.resetScheduleToDefault(this)
            Toast.makeText(this, "Schedule reset to defaults", Toast.LENGTH_SHORT).show()
            rebuildRows()
        }

        rebuildRows()
    }

    private fun rebuildRows() {
        binding.rowContainer.removeAllViews()
        rows.clear()
        val inflater = LayoutInflater.from(this)

        for (slot in Prefs.getSchedule(this)) {
            val rowView = inflater.inflate(R.layout.row_schedule_edit, binding.rowContainer, false)
            rowView.findViewById<TextView>(R.id.tvSlotLabel).text = slot.label
            val etStart = rowView.findViewById<EditText>(R.id.etStart)
            val etEnd = rowView.findViewById<EditText>(R.id.etEnd)
            etStart.setText(slot.startLabel())
            etEnd.setText(slot.endLabel())

            rowView.findViewById<TextView>(R.id.btnRemoveRow).setOnClickListener {
                binding.rowContainer.removeView(rowView)
                rows.removeAll { it.rowView == rowView }
            }

            binding.rowContainer.addView(rowView)
            rows.add(RowRefs(rowView, slot.label, etStart, etEnd))
        }
    }

    private fun saveSchedule() {
        val slots = mutableListOf<ScheduleSlot>()
        var hadError = false

        for (row in rows) {
            val start = ScheduleSlot.parseTimeToMinutes(row.etStart.text.toString())
            val end = ScheduleSlot.parseTimeToMinutes(row.etEnd.text.toString())
            if (start == null || end == null) {
                hadError = true
                continue
            }
            slots.add(ScheduleSlot(row.label, start, end))
        }

        Prefs.saveSchedule(this, slots)

        if (hadError) {
            Toast.makeText(this, "Saved, but some times weren't valid 4-digit HHMM and were skipped", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Schedule saved", Toast.LENGTH_SHORT).show()
        }
    }
}
