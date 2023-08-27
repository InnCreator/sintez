package ru.inncreator.sintez.ui.start

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import ru.inncreator.sintez.R
import ru.inncreator.sintez.databinding.FragmentInstructionsBinding

class InstructionsFragment : Fragment() {

    private var _binding: FragmentInstructionsBinding? = null
    private val binding
        get() = _binding!!

    enum class State {
        Preload,
        Idle,
        Instruction
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding =
            FragmentInstructionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateUi(State.Idle)

        initIdle()
        initPreload()
        initInstruction()

    }

    private fun initIdle() {
        binding.imageFull.setOnClickListener {
            updateUi(State.Preload)
        }

    }

    private fun initPreload() {
        binding.arrowLeft.setOnClickListener {
            updateUi(State.Instruction)
        }
    }

    private fun initInstruction() {

        val adapter = InstructionAdapter { bool: Boolean ->
            if (bool) {
                binding.start.setBackgroundColor(requireContext().getColor(R.color.green))
                binding.start.setTextColor(requireContext().getColor(R.color.white))
            } else {
                binding.start.setBackgroundColor(requireContext().getColor(R.color.transparent))
                binding.start.setTextColor(requireContext().getColor(R.color.green))
            }

        }
        binding.pager.adapter = adapter

        binding.start.setOnClickListener {
            findNavController().navigate(R.id.action_instructionsFragment_to_mainFragment)
        }

    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun updateUi(state: State) {
        when (state) {
            State.Idle -> {
                binding.bottomContainer.visibility = View.GONE
                binding.imageFull.visibility = View.VISIBLE
                binding.imageFull.setImageDrawable(requireActivity().getDrawable(R.drawable.instruction_idle))
                binding.pager.visibility = View.GONE
            }
            State.Preload -> {
                binding.imageFull.visibility = View.VISIBLE
                binding.imageFull.setImageDrawable(requireActivity().getDrawable(R.drawable.instruction_preload))
                binding.bottomContainer.visibility = View.VISIBLE
                binding.pager.visibility = View.GONE

            }
            State.Instruction -> {
                binding.bottomContainer.visibility = View.VISIBLE
                binding.start.visibility = View.VISIBLE
                binding.arrowLeft.visibility = View.GONE
                binding.arrowRight.visibility = View.VISIBLE
                binding.imageFull.visibility = View.GONE
                binding.pager.visibility = View.VISIBLE
            }
        }
    }
}