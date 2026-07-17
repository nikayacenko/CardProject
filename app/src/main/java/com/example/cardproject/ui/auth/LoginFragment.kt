package com.example.cardproject.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.cardproject.R
import com.example.cardproject.databinding.FragmentLoginBinding
import com.example.cardproject.ui.deck.DeckListFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                binding.loginButton.isEnabled = !state.isLoading

                state.error?.let {
                    Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                }

                if (state.isAuthenticated) {
                    // Переход на главный экран
                    navigateToMain()
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.loginButton.setOnClickListener {
            val username = binding.usernameInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            if (username.isBlank() || password.isBlank()) {
                Toast.makeText(requireContext(), "Заполните все поля", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.login(username, password)
        }

        binding.registerLink.setOnClickListener {
            // Переход на экран регистрации
            navigateToRegister()
        }
    }

    private fun navigateToMain() {
        // Очищаем back stack и переходим на DeckListFragment
        val fragment = DeckListFragment()

        // Полностью очищаем back stack
        parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)  // ← используем ваш контейнер
            .commit()
    }

    private fun navigateToRegister() {
        val fragment = RegisterFragment()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)  // ← используем ваш контейнер
            .addToBackStack("register")
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}