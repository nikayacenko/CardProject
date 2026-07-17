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
import com.example.cardproject.databinding.FragmentRegisterBinding
import com.example.cardproject.ui.deck.DeckListFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
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
                // Показываем/скрываем прогресс
                binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                binding.registerButton.isEnabled = !state.isLoading

                // Показываем ошибку
                state.error?.let {
                    Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                }

                // При успешной регистрации переходим на главный экран
                if (state.isAuthenticated) {
                    Toast.makeText(
                        requireContext(),
                        "Добро пожаловать, ${state.user?.username ?: "пользователь"}!",
                        Toast.LENGTH_SHORT
                    ).show()
                    navigateToMain()
                }
            }
        }
    }

    private fun setupClickListeners() {
        // Кнопка "Зарегистрироваться"
        binding.registerButton.setOnClickListener {
            val username = binding.usernameInput.text.toString().trim()
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()
            val confirmPassword = binding.confirmPasswordInput.text.toString().trim()

            // Валидация
            if (username.isBlank() || password.isBlank()) {
                Toast.makeText(requireContext(), "Заполните все обязательные поля", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (username.length < 3) {
                Toast.makeText(requireContext(), "Имя пользователя должно содержать минимум 3 символа", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(requireContext(), "Пароль должен содержать минимум 6 символов", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(requireContext(), "Пароли не совпадают", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Отправляем запрос на регистрацию
            val emailForRegister = if (email.isNotBlank()) email else null
            viewModel.register(username, password, emailForRegister)
        }

        // Ссылка "Уже есть аккаунт? Войти"
        binding.loginLink.setOnClickListener {
            navigateToLogin()
        }
    }

    private fun navigateToMain() {
        val fragment = DeckListFragment()

        // Полностью очищаем back stack
        parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)  // ← используем ваш контейнер
            .commit()
    }

    private fun navigateToLogin() {
        parentFragmentManager.popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}