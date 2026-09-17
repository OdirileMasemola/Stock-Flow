package com.example.stockflow.ui.inventory

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.example.stockflow.R
import com.example.stockflow.data.remote.CategoryDto
import com.example.stockflow.data.remote.ProductDto
import com.example.stockflow.databinding.ActivityAddProductBinding
import com.example.stockflow.ui.common.ProductImages
import com.example.stockflow.ui.common.SystemBars
import com.example.stockflow.ui.scanner.BarcodeScannerActivity

/**
 * Create a new product or edit an existing one.
 * Category selector: cached categories + "Add new category" with inline name field.
 */
class AddProductActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddProductBinding
    private val viewModel: AddProductViewModel by viewModels()

    private var productId: Int? = null
    private var previewUri: Uri? = null
    private var categoryOptions: List<CategoryDto> = emptyList()
    private var selectedCategory: CategoryDto? = null
    private var suppressSpinnerCallback = false

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            previewUri = uri
            viewModel.setPendingImage(uri)
            showLocalPreview(uri)
        }
    }

    private val scanBarcode = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val value = result.data
            ?.getStringExtra(BarcodeScannerActivity.EXTRA_SCAN_VALUE)
            ?.trim()
            .orEmpty()
        if (value.isNotEmpty()) {
            binding.etSku.setText(value)
            binding.etSku.setSelection(value.length)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddProductBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.applyLight(this, binding.root)

        // Allow editing temp local products (negative ids) as well as remote (>0).
        val rawId = intent.getIntExtra(EXTRA_PRODUCT_ID, 0)
        productId = rawId.takeIf { it != 0 }

        if (productId != null) {
            binding.toolbar.title = getString(R.string.edit_product_title)
            viewModel.loadProduct(productId!!)
        } else {
            binding.toolbar.title = getString(R.string.add_product_title)
            showEmptyImageState()
        }

        binding.toolbar.setNavigationOnClickListener { finish() }

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

        binding.btnScanSku.setOnClickListener {
            scanBarcode.launch(Intent(this, BarcodeScannerActivity::class.java))
        }

        binding.spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressSpinnerCallback) return
                val labels = binding.spinnerCategory.adapter?.count ?: 0
                if (labels == 0) return
                // Last item is always "Add new category"
                val isAddNew = position == labels - 1
                viewModel.setAddingNewCategory(isAddNew)
                setNewCategoryVisible(isAddNew)
                selectedCategory = if (isAddNew || position >= categoryOptions.size) {
                    null
                } else {
                    categoryOptions[position]
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        binding.btnSave.setOnClickListener {
            val addingNew = viewModel.addingNewCategory.value == true
            viewModel.saveProduct(
                productId = productId,
                name = binding.etName.text.toString(),
                sku = binding.etSku.text.toString(),
                costPriceText = binding.etCostPrice.text.toString(),
                sellingPriceText = binding.etSellingPrice.text.toString(),
                stockLevelText = binding.etStockLevel.text.toString(),
                minStockLevelText = binding.etMinStock.text.toString(),
                selectedCategory = selectedCategory,
                newCategoryName = binding.etNewCategory.text?.toString(),
                isAddingNewCategory = addingNew,
                supplierIdText = binding.etSupplierId.text.toString()
            )
        }

        viewModel.categories.observe(this) { categories ->
            categoryOptions = categories
            bindCategorySpinner(categories)
            maybeSelectLoadedProductCategory()
        }

        viewModel.addingNewCategory.observe(this) { adding ->
            setNewCategoryVisible(adding == true)
        }

        viewModel.loadedProduct.observe(this) { product ->
            product?.let {
                fillForm(it)
                maybeSelectLoadedProductCategory()
            }
        }

        viewModel.formState.observe(this) { state ->
            when (state) {
                is AddProductViewModel.FormState.Idle -> setLoading(false)
                is AddProductViewModel.FormState.Loading -> setLoading(true)
                is AddProductViewModel.FormState.Success -> {
                    setLoading(false)
                    val message = when {
                        state.savedOffline -> getString(R.string.saved_offline)
                        state.isUpdate -> getString(R.string.product_updated)
                        else -> getString(R.string.product_created)
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

    private fun bindCategorySpinner(categories: List<CategoryDto>) {
        val labels = categories.map { it.name } + getString(R.string.category_add_new)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        suppressSpinnerCallback = true
        binding.spinnerCategory.adapter = adapter
        // Keep current selection if possible
        val currentId = selectedCategory?.id
            ?: viewModel.loadedProduct.value?.categoryId
        val index = categories.indexOfFirst { it.id == currentId }
        if (index >= 0) {
            binding.spinnerCategory.setSelection(index)
            selectedCategory = categories[index]
            viewModel.setAddingNewCategory(false)
        } else if (categories.isNotEmpty() && selectedCategory == null && viewModel.addingNewCategory.value != true) {
            binding.spinnerCategory.setSelection(0)
            selectedCategory = categories[0]
            viewModel.setAddingNewCategory(false)
        }
        suppressSpinnerCallback = false
    }

    private fun maybeSelectLoadedProductCategory() {
        val product = viewModel.loadedProduct.value ?: return
        val index = categoryOptions.indexOfFirst { it.id == product.categoryId }
        if (index >= 0) {
            suppressSpinnerCallback = true
            binding.spinnerCategory.setSelection(index)
            selectedCategory = categoryOptions[index]
            viewModel.setAddingNewCategory(false)
            setNewCategoryVisible(false)
            suppressSpinnerCallback = false
        }
    }

    private fun setNewCategoryVisible(visible: Boolean) {
        val vis = if (visible) View.VISIBLE else View.GONE
        binding.tvNewCategoryLabel.visibility = vis
        binding.etNewCategory.visibility = vis
    }

    private fun fillForm(product: ProductDto) {
        binding.etName.setText(product.name)
        binding.etSku.setText(product.sku.orEmpty())
        binding.etCostPrice.setText(product.costPrice.toString())
        binding.etSellingPrice.setText(product.sellingPrice.toString())
        binding.etStockLevel.setText(product.stockLevel.toString())
        binding.etMinStock.setText(product.minStockLevel.toString())
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
        binding.btnScanSku.isEnabled = !loading
        binding.spinnerCategory.isEnabled = !loading
        binding.etNewCategory.isEnabled = !loading
        if (loading) {
            binding.tvFormError.visibility = View.GONE
        }
    }

    companion object {
        const val EXTRA_PRODUCT_ID = "extra_product_id"
    }
}
