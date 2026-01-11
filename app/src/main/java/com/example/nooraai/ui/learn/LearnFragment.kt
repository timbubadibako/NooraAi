import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.nooraai.R

data class Lesson(val index: Int, val title: String, val duration: String)

class LessonAdapter(
    private var items: List<Lesson>,
    private val onClick: (Lesson) -> Unit
) : RecyclerView.Adapter<LessonAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvIndex: TextView = view.findViewById(R.id.tvIndex)
        val tvTitle: TextView = view.findViewById(R.id.tvLessonTitle)
        val tvDuration: TextView = view.findViewById(R.id.tvDuration)

        init {
            view.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onClick(items[pos])
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.layout_lesson_item, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val lesson = items[position]
        holder.tvIndex.text = lesson.index.toString()
        holder.tvTitle.text = lesson.title
        holder.tvDuration.text = lesson.duration
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<Lesson>) {
        items = newItems
        notifyDataSetChanged()
    }
}