package ru.inncreator.sintez.ui.start

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.inncreator.sintez.R
import ru.inncreator.sintez.databinding.InstructionAdapterBinding

class InstructionAdapter(private val function: (Boolean) -> Unit) :
    RecyclerView.Adapter<PagerVH>() {

    private val listItems = listOf(
        R.drawable.instruction_1,
        R.drawable.instruction_2,
        R.drawable.instruction_3,
        R.drawable.instruction_4,
        R.drawable.instruction_5,
        R.drawable.instruction_6,
        R.drawable.instruction_7,
        R.drawable.instruction_8,
        R.drawable.instruction_9,
        R.drawable.instruction_10
    )


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagerVH =
        PagerVH(
            InstructionAdapterBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun getItemCount(): Int = listItems.size

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onBindViewHolder(holder: PagerVH, position: Int) {
        val drawable = holder.binding.root.context.getDrawable(listItems[position])
        holder.binding.image.setImageDrawable(drawable)
        function.invoke(position == itemCount - 1)

    }

}

class PagerVH(val binding: InstructionAdapterBinding) : RecyclerView.ViewHolder(binding.root)
