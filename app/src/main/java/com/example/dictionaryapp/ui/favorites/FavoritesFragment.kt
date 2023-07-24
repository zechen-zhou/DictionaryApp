package com.example.dictionaryapp.ui.favorites

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.Navigation
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.dictionaryapp.R
import com.example.dictionaryapp.data.MyContract
import com.example.dictionaryapp.data.MyOpenHelper
import com.example.dictionaryapp.databinding.FragmentFavoritesBinding
import com.example.dictionaryapp.ui.home.HomeFragment
import com.google.android.material.snackbar.Snackbar
import java.io.Serializable

class FavoritesFragment : Fragment(), MenuProvider {

    private var _binding: FragmentFavoritesBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    // Members of the companion object can be called simply by using the class name as the qualifier
    companion object {
        val wordDefinitions = ArrayList<HomeFragment.WordDefinition>()
        var db: SQLiteDatabase? = null
        var recyclerView: RecyclerView? = null
        var hintView: TextView? = null
        var starView: ImageView? = null
        var adt: MyFavoritesAdapter? = null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        val root: View = binding.root

        recyclerView = binding.recyclerView
        hintView = binding.hintView
        starView = binding.starView

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // https://stackoverflow.com/a/73350979
        activity?.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        // Clears wordDefinitions ArrayList<>, because onViewCreated is called when rotate the screen
        // ,which means the word definitions are loaded from the database again
        wordDefinitions.clear()

        val opener = MyOpenHelper(requireContext())
        db = opener.writableDatabase

        // Adds dividers and space between items in RecyclerView (https://stackoverflow.com/a/41201865)
        recyclerView?.addItemDecoration(
            DividerItemDecoration(
                getContext(),
                DividerItemDecoration.VERTICAL
            )
        )

        // Reads information from the database
        val query = "Select * from " + MyContract.Entry.TABLE_NAME + ";"
        val results = db?.rawQuery(query, null)

        // Adds the query results to the ArrayList "wordDefinitions"
        addWordDefinitions(results, wordDefinitions)

        // Calls close() on 'results' to release its resources
        results?.close()

        // Creates an object of MyFavoritesAdapter
        adt = MyFavoritesAdapter(wordDefinitions)

        /**
         * Sets adapter
         *
         * Since it will load the data to the screen while initializing the adapter, so first load
         * the word definitions from the database and then insert them into ArrayList<> BEFORE
         * setting the adapter of the recycler view
         */
        recyclerView?.adapter = adt

        // Sets layoutManager to be a vertical LinearLayoutManager
        recyclerView?.layoutManager = LinearLayoutManager(context)
    }

    class MyFavoritesRowViews(
        itemView: View,
        wDefinitions_2: ArrayList<HomeFragment.WordDefinition>,
        adapter: MyFavoritesAdapter // constructor parameters
    ) : RecyclerView.ViewHolder(itemView) {

        // Properties
        val word: TextView
        val type: TextView
        val definition: TextView
        val favCheckBox: CheckBox

        init {
            word = itemView.findViewById(R.id.word)
            type = itemView.findViewById(R.id.type)
            definition = itemView.findViewById(R.id.definition)
            favCheckBox = itemView.findViewById(R.id.favCheckBox)

            // Defines click listener for the MyRowViews' itemView (i.e the listener is for when you click an item on the RecyclerView)
            itemView.setOnClickListener { _ ->
                val position = adapterPosition
                val thisDefinition = wDefinitions_2[position]
                val bundle = Bundle()
                val myMap = mutableMapOf<String, String>()

                bundle.putString("word", thisDefinition.word)

                // Only "pronunciation" and "example" could have null value
                if (thisDefinition.pronunciation != "null") {
                    bundle.putString("pronunciation", thisDefinition.pronunciation)
                }

                myMap["type"] = thisDefinition.type
                myMap["definition"] = thisDefinition.definition

                if (thisDefinition.example != "null") {
                    myMap["example"] = thisDefinition.example
                }

                val keyList = ArrayList<String>(myMap.keys)

                bundle.putStringArrayList("keyList", keyList)
                bundle.apply { putSerializable("myMap", myMap as Serializable) }

                // Passes the word definition and navigates to "detailDefinitionFragment"
                Navigation.findNavController(itemView)
                    .navigate(R.id.action_navigation_favorites_to_detailDefinitionFragment, bundle)
            }

            favCheckBox.setOnCheckedChangeListener { _, isChecked ->
                // The word definition position that you clicked on (i.e. the star icon position that you tap on)
                val position = adapterPosition

                // If favCheckBox is unchecked, delete the word definition that was selected
                if (!isChecked) {
                    // Stores the word definition before removing it from the ArrayList
                    val removedDefinition = wDefinitions_2.get(position)

                    // Removes the word definition from ArrayList
                    wDefinitions_2.removeAt(position)

                    // Notifies the adapter object that there's data has been removed
                    adapter!!.notifyItemRemoved(position)

                    // Deletes a word definition from the database where an id equals the word definition that was selected
                    db?.delete(
                        MyContract.Entry.TABLE_NAME,
                        "_id=?",
                        arrayOf<String>(removedDefinition.id.toString())
                    )

                    // Undo the deletion using Snackbar
                    Snackbar.make(word, "Removed", Snackbar.LENGTH_LONG)
                        .setAction("Undo", View.OnClickListener {
                            // Reinserts the word definition back into the ArrayList
                            wDefinitions_2.add(position, removedDefinition)

                            // Notifies the adapter object that there's data has been inserted
                            adapter!!.notifyItemInserted(position)

                            // Reinserts the word definition into the database
                            db!!.execSQL(
                                "Insert into " + MyContract.Entry.TABLE_NAME + " values(?,?,?,?,?,?);",
                                arrayOf<String>(
                                    removedDefinition.id.toString(),
                                    removedDefinition.word,
                                    removedDefinition.pronunciation,
                                    removedDefinition.type,
                                    removedDefinition.definition,
                                    removedDefinition.example
                                )
                            )
                        })
                        .show()
                }

                // Shows hint view if no word definitions are saved
                if (wordDefinitions.size == 0) {
                    hintView!!.visibility = View.VISIBLE
                    starView!!.visibility = View.VISIBLE
                } else {
                    hintView!!.visibility = View.INVISIBLE
                    starView!!.visibility = View.INVISIBLE
                }
            }
        }
    }

    class MyFavoritesAdapter(private val wDefinitions_1: ArrayList<HomeFragment.WordDefinition>) :
        RecyclerView.Adapter<MyFavoritesRowViews>() {
        // Creates new views (invoked by the layout manager)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyFavoritesRowViews {
            // Creates a new view, which defines the UI of the list item
            val initRow = LayoutInflater.from(parent.context)
                .inflate(R.layout.favorites_row, parent, false)

            return MyFavoritesRowViews(initRow, wDefinitions_1, this)
        }

        // Replaces the contents of a view (invoked by the layout manager)
        override fun onBindViewHolder(holder: MyFavoritesRowViews, position: Int) {

            // Initializes the state of 'favCheckBox' to be checked or unchecked
            if (wDefinitions_1.get(position).saveState == HomeFragment.SAVED) {
                holder.favCheckBox.isChecked = true
            } else if (wDefinitions_1.get(position).saveState == HomeFragment.NOT_SAVED) {
                holder.favCheckBox.isChecked = false
            }

            holder.word.text = wDefinitions_1.get(position).word
            holder.type.text = wDefinitions_1.get(position).type
            holder.definition.text = wDefinitions_1.get(position).definition
        }

        override fun getItemCount(): Int {
            return wDefinitions_1.size
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        // Closes the database
        db?.close()

        super.onDestroy()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.options_menu, menu)

        // https://stackoverflow.com/q/61010340
        val searchItem: MenuItem = menu.findItem(R.id.app_bar_search)
        val searchView: SearchView = searchItem.actionView as SearchView

        searchView.setOnQueryTextListener(object :
            SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // An ArrayList to store the filtered word definitions
                val filteredWordDefinitions = ArrayList<HomeFragment.WordDefinition>()

                // Queries the database
                val query =
                    "Select * from " + MyContract.Entry.TABLE_NAME + " where " + MyContract.Entry.COLUMN_NAME_WORD + " like " + "\'%" + newText + "%\'" + ";"
                val results = db?.rawQuery(query, null)

                // Adds the query results to the ArrayList "filteredWordDefinitions"
                addWordDefinitions(results, filteredWordDefinitions)

                // Calls close() on 'results' to release its resources
                results?.close()

                // Creates an object of "MyFavoritesAdapter": "myAdapter"
                val myAdapter = MyFavoritesAdapter(filteredWordDefinitions)

                // Sets adapter
                recyclerView?.adapter = myAdapter

                // Sets layoutManager to be a vertical LinearLayoutManager
                recyclerView?.layoutManager = LinearLayoutManager(context)

                return true
            }

        })
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.app_bar_search -> {
                view?.let {
//                    Navigation.findNavController(it)
//                        .navigate(R.id.action_navigation_favorites_to_searchableFragment)
                }
                true
            }

            else -> false
        }
    }

    /**
     * Adds the query results from the database to a target list.
     *
     * @param queryResults the query results from the database
     * @param targetList the ArrayList that store the query results
     */
    private fun addWordDefinitions(
        queryResults: Cursor?,
        targetList: ArrayList<HomeFragment.WordDefinition>
    ) {
        if (queryResults != null) {

            // Initializes each of the column indexes that you want right after the query
            val idCol = queryResults.getColumnIndex("_id")
            val wordCol = queryResults.getColumnIndex(MyContract.Entry.COLUMN_NAME_WORD)
            val pronunciationCol =
                queryResults.getColumnIndex(MyContract.Entry.COLUMN_NAME_PRONUNCIATION)
            val typeCol = queryResults.getColumnIndex(MyContract.Entry.COLUMN_NAME_TYPE)
            val definitionCol = queryResults.getColumnIndex(MyContract.Entry.COLUMN_NAME_DEFINITION)
            val exampleCol = queryResults.getColumnIndex(MyContract.Entry.COLUMN_NAME_EXAMPLE)

            // Sets saveState to SAVED since these definitions are retrieved from the database
            val saveState = HomeFragment.SAVED

            while (queryResults.moveToNext()) {
                // For each row that the cursor(i.e. results) is pointing at, get the values associated with each column
                val id = queryResults.getLong(idCol)
                val word = queryResults.getString(wordCol)
                val pronunciation = queryResults.getString(pronunciationCol)
                val type = queryResults.getString(typeCol)
                val definition = queryResults.getString(definitionCol)
                val example = queryResults.getString(exampleCol)

                // Adds the created wordDefinitions object to the ArrayList "targetList"
                targetList.add(
                    HomeFragment.WordDefinition(
                        word,
                        pronunciation,
                        type,
                        definition,
                        example,
                        saveState,
                        id
                    )
                )
            }
        }
    }
}