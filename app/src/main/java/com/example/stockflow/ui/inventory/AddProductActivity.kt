package com.example.stockflow.ui.inventory

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.example.stockflow.R
import com.example.stockflow.data.remote.ProductDto
import com.example.stockflow.databinding.ActivityAddProductBinding
import com.example.stockflow.ui.common.ProductImages
import com.example.stockflow.ui.common.SystemBars

/**
 * Create a new product or edit an existing one.
 * Pass [EXTRA_PRODUCT_ID] to open in edit mode.
 */
class AddProductActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddProductBinding
    private val viewModel: AddProductViewModel by viewModels()

    /** Null when creating; non-null when editing. */
    private var productId: Int? = null

    private var previewUri: Uri? = null

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            previewUri = uri
            viewModel.setPendingImage(uri)
            showLocalPreview(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddProductBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.applyLight(this, binding.root)

        productId = intent.getIntExtra(EXTRA_PRODUCT_ID, -1).takeIf { it > 0 }

        if (productId != null) {
            binding.toolbar.title = getString(R.string.edit_product_title)
            viewModel.loadProduct(productId!!)
        } else {
            binding.toolbar.title = getString(R.string.add_product_title)
            showEmptyImageState()
        }

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.imagePickerArea.setOnClickListener {
            pickImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        binding.btnRemoveImage.setOnClickListener {
            previewUri = null
            viewModel.clearImage()
            showEmptyImageState()
        }

        binding.btnSave.setOnClickListener {
            viewModel.saveProduct(
                productId = productId,
                name = binding.etName.text.toString(),
                sku = binding.etSku.text.toString(),
                costPriceText = binding.etCostPrice.text.toString(),
                sellingPriceText = binding.etSellingPrice.text.toString(),
                stockLevelText = binding.etStockLevel.text.toString(),
                minStockLevelText = binding.etMinStock.text.toString(),
                categoryIdText = binding.etCategoryId.text.toString(),
                supplierIdText = binding.etSupplierId.text.toString()
            )
        }

        viewModel.loadedProduct.observe(this) { product ->
            product?.let { fillForm(it) }
        }

        viewModel.formState.observe(this) { state ->
            when (state) {
                is AddProductViewModel.FormState.Idle -> setLoading(false)
                is AddProductViewModel.FormState.Loading -> setLoading(true)
                is AddProductViewModel.FormState.Success -> {
                    setLoading(false)
                    val message = if (state.isUpdate) {
                        getString(R.string.product_updated)
                    } else {
                        getString(R.string.product_created)
                    }
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
                is AddProductViewModel.FormState.Error -> {
                    setLoading(false)
                    binding.tvFormError.visibility = View.VISIBLE
                    binding.tvFormError.text = state.message
                }
                null -> Unit
            }
        }
    }

    private fun fillForm(product: ProductDto) {
        binding.etName.setText(product.name)
        binding.etSku.setText(product.sku.orEmpty())
        binding.etCostPrice.setText(product.costPrice.toString())
        binding.etSellingPrice.setText(product.sellingPrice.toString())
        binding.etStockLevel.setText(product.stockLevel.toString())
        binding.etMinStock.setText(product.minStockLevel.toString())
        binding.etCategoryId.setText(product.categoryId.toString())
        binding.etSupplierId.setText(product.supplierId?.toString().orEmpty())

        if (previewUri != null) {
            showLocalPreview(previewUri!!)
        } else {
            val remote = ProductImages.resolveUrl(product.imageUrl)
            if (remote.isNullOrBlank()) {
                showEmptyImageState()
            } else {
                showRemotePreview(remote)
            }
        }
    }

    private fun showLocalPreview(uri: Uri) {
        binding.imagePlaceholder.visibility = View.GONE
        binding.ivProductImage.visibility = View.VISIBLE
        binding.btnRemoveImage.visibility = View.VISIBLE
        binding.tvImageHint.text = getString(R.string.change_product_image)
        binding.ivProductImage.load(uri) {
            placeholder(R.drawable.bg_product_image_placeholder)
            error(R.drawable.bg_product_image_placeholder)
        }
    }

    private fun showRemotePreview(url: String) {
        binding.imagePlaceholder.visibility = View.GONE
        binding.ivProductImage.visibility = View.VISIBLE
        binding.btnRemoveImage.visibility = View.VISIBLE
        binding.tvImageHint.text = getString(R.string.change_product_image)
        binding.ivProductImage.load(url) {
            placeholder(R.drawable.bg_product_image_placeholder)
            error(R.drawable.bg_product_image_placeholder)
        }
    }

    private fun showEmptyImageState() {
        binding.ivProductImage.visibility = View.GONE
        binding.ivProductImage.setImageDrawable(null)
        binding.imagePlaceholder.visibility = View.VISIBLE
        binding.btnRemoveImage.visibility = View.GONE
        binding.tvImageHint.text = getString(R.string.add_product_image)
    }

    private fun setLoading(loading: Boolean) {
        binding.progressSaving.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled = !loading
        binding.imagePickerArea.isEnabled = !loading
        binding.btnRemoveImage.isEnabled = !loading
        if (loading) {
            binding.tvFormError.visibility = View.GONE
        }
    }

    companion object {
        const val EXTRA_PRODUCT_ID = "extra_product_id"
    }
}
