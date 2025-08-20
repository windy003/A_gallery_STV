package com.example.photogallery

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.photogallery.data.AppDatabase
import com.example.photogallery.data.Collection
import com.example.photogallery.databinding.ActivityCollectionsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CollectionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCollectionsBinding
    private val viewModel: CollectionsViewModel by viewModels {
        CollectionsViewModelFactory(AppDatabase.getDatabase(this).collectionDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCollectionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val adapter = CollectionAdapter(
            onItemClick = {
                val intent = Intent(this, CollectionDetailActivity::class.java).apply {
                    putExtra(CollectionDetailActivity.EXTRA_COLLECTION_ID, it.id)
                    putExtra(CollectionDetailActivity.EXTRA_COLLECTION_NAME, it.name)
                }
                startActivity(intent)
            },
            onOptionsMenuClick = { view, collection ->
                showOptionsMenu(view, collection)
            }
        )

        binding.collectionsRecyclerView.adapter = adapter
        binding.collectionsRecyclerView.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            viewModel.allCollections.collectLatest { collections ->
                adapter.submitList(collections)
            }
        }

        binding.fabAddCollection.setOnClickListener {
            showCreateCollectionDialog()
        }
    }

    private fun showCreateCollectionDialog() {
        val editText = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Create New Collection")
            .setView(editText)
            .setPositiveButton("Create") { _, _ ->
                val name = editText.text.toString()
                if (name.isNotBlank()) {
                    viewModel.insertCollection(name)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showOptionsMenu(view: View, collection: Collection) {
        PopupMenu(this, view).apply {
            menuInflater.inflate(R.menu.collection_options_menu, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_rename -> {
                        showRenameCollectionDialog(collection)
                        true
                    }
                    R.id.action_delete -> {
                        showDeleteCollectionDialog(collection)
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun showRenameCollectionDialog(collection: Collection) {
        val editText = EditText(this)
        editText.setText(collection.name)
        AlertDialog.Builder(this)
            .setTitle("Rename Collection")
            .setView(editText)
            .setPositiveButton("Rename") { _, _ ->
                val newName = editText.text.toString()
                if (newName.isNotBlank()) {
                    viewModel.updateCollection(collection.copy(name = newName))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteCollectionDialog(collection: Collection) {
        AlertDialog.Builder(this)
            .setTitle("Delete Collection")
            .setMessage("Are you sure you want to delete '${collection.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteCollection(collection)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
