package com.example.stockflow.ui.sales

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.stockflow.R
import com.example.stockflow.databinding.ActivityCartBinding
import com.example.stockflow.ui.common.SystemBars

class CartActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCartBinding
    private val viewModel: CartViewModel by viewModels()
    private lateinit var cartAdapter: CartAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val night = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        SystemBars.apply(
            activity = this,
            root = binding.cartRoot,
            statusBarColor = getColor(R.color.page_background),
            navigationBarColor = getColor(R.color.surface),
            lightStatusBars = !night,
            lightNavigationBars = !night
        )

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnBackToPos.setOnClickListener { finish() }

        cartAdapter = CartAdapter(
            onIncrease = { viewModel.increase(it) },
            onDecrease = { viewModel.decrease(it) },
            onRemove = { viewModel.remove(it) }
        )
        binding.rvCart.layoutManager = LinearLayoutManager(this)
        binding.rvCart.adapter = cartAdapter

        binding.btnClearCart.setOnClickListener { viewModel.clearCart() }
        binding.btnCompleteSale.setOnClickListener { viewModel.completeSale() }

        binding.rgPayment.setOnCheckedChangeListener { _, checkedId ->
            val method = when (checkedId) {
                R.id.rbCard -> "Card"
                R.id.rbOther -> "Other"
                else -> "Cash"
            }
            viewModel.selectPaymentMethod(method)
        }

        viewModel.cartState.observe(this) { renderCart(it) }
        viewModel.paymentMethod.observe(this) { method ->
            val id = when (method) {
                "Card" -> R.id.rbCard
                "Other" -> R.id.rbOther
                else -> R.id.rbCash
            }
            if (binding.rgPayment.checkedRadioButtonId != id) {
                binding.rgPayment.check(id)
            }
        }
        viewModel.checkoutState.observe(this) { renderCheckout(it) }
    }

    private fun renderCart(state: CartSession.CartUiState) {
        val empty = state.lines.isEmpty()
        binding.emptyCart.isVisible = empty
        binding.rvCart.isVisible = !empty
        binding.btnClearCart.isEnabled = !empty
        binding.btnCompleteSale.isEnabled = !empty
        binding.checkoutPanel.alpha = if (empty) 0.55f else 1f

        cartAdapter.submitList(state.lines)
        binding.tvCartTotal.text = getString(R.string.price_format, state.total)
        binding.toolbar.title = if (empty) {
            getString(R.string.pos_cart_title)
        } else {
            getString(R.string.pos_cart_title_count, state.itemCount)
        }
    }

    private fun renderCheckout(state: CartViewModel.CheckoutUiState) {
        val loading = state is CartViewModel.CheckoutUiState.Loading
        binding.progressCheckout.isVisible = loading
        binding.btnCompleteSale.isEnabled =
            !loading && (viewModel.cartState.value?.lines?.isNotEmpty() == true)
        binding.btnCompleteSale.alpha = if (loading) 0.5f else 1f

        when (state) {
            is CartViewModel.CheckoutUiState.Idle -> Unit
            is CartViewModel.CheckoutUiState.Loading -> Unit
            is CartViewModel.CheckoutUiState.Success -> {
                Toast.makeText(
                    this,
                    getString(
                        R.string.sale_completed,
                        state.sale.id,
                        getString(R.string.price_format, state.sale.totalAmount)
                    ),
                    Toast.LENGTH_LONG
                ).show()
                viewModel.clearCheckoutMessage()
                finish()
            }
            is CartViewModel.CheckoutUiState.Error -> {
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                viewModel.clearCheckoutMessage()
            }
        }
    }
}
