package com.example.dictionaryapp.ui.home

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.dictionaryapp.BuildConfig
import com.example.dictionaryapp.R
import com.example.dictionaryapp.data.MyContract
import com.example.dictionaryapp.data.MyOpenHelper
import com.example.dictionaryapp.databinding.FragmentHomeBinding
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.URLProtocol
import io.ktor.http.path
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.Serializable
import java.net.URLEncoder

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    // Needs a job to handle the coroutine cancellation to prevent leaks (https://stackoverflow.com/a/59609020)
    val job = Job()
    val uiScope = CoroutineScope(Dispatchers.Main + job)

    // Coroutine exception handler (https://stackoverflow.com/a/71004286)
    val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        throwable.printStackTrace()
    }

    private lateinit var progressBar: ProgressBar

    companion object {
        val wordDefinitions = ArrayList<WordDefinition>()
        var searchView: SearchView? = null
        var wordLabel: TextView? = null
        var definitionLabel: TextView? = null
        var recyclerView: RecyclerView? = null
        var adt: MyAdapter? = null

        var db: SQLiteDatabase? = null

        // 1 means the word definition has been saved in the database, 0 means not saved yet
        const val SAVED = 1
        const val NOT_SAVED = 0
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {

        // Creates a ViewModel object
        val homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)

        // Inflates the layout for this fragment
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        searchView = binding.searchView
        wordLabel = binding.wordLabel
        definitionLabel = binding.definitionLabel
        recyclerView = binding.recyclerView
        progressBar = binding.progressBar

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val opener = MyOpenHelper(requireContext())
        db = opener.writableDatabase

        // Adds dividers and space between items in RecyclerView (https://stackoverflow.com/a/41201865)
        recyclerView?.addItemDecoration(
            DividerItemDecoration(
                getContext(),
                DividerItemDecoration.VERTICAL
            )
        )

        // Displays label "Definitions of ..." when screen is rotated and ArrayList wordDefinitions is not null
        if (!wordDefinitions.isEmpty()) {
            wordLabel?.text = wordDefinitions.get(0).word
            definitionLabel?.text = "Definitions of "
        }

        // Creates an object of MyAdapter
        adt = MyAdapter()

        // recyclerView.setAdapter(adt); in Java
        recyclerView?.adapter = adt
        recyclerView?.layoutManager = LinearLayoutManager(context)

        // Instantiates the client by creating the HttpClient class instance
        val client = HttpClient() {
            // Configures the client
            defaultRequest {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "owlbot.info"
                    path("api/v4/dictionary/")
                }

                val apiKey = BuildConfig.API_KEY
                val headerValue = "Token " + apiKey
                header("Authorization", headerValue)
            }
        }

        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {

                // Makes progress bar to be visible when we retrieving data
                progressBar.visibility = View.VISIBLE

                // Makes it invisible when search a new word
                wordLabel?.visibility = View.INVISIBLE
                definitionLabel?.visibility = View.INVISIBLE

                // Adds a coroutine exception handler in launch code (able to log errors to Logcat)
                uiScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
                    // asyncOperation

                    // Clears the last search results
                    wordDefinitions.clear()

                    // Removes space if it contains only one word, such that "hello " become "hello"
                    var querytext = query

                    if (!hasMultipleWords(querytext!!)) {
                        val spacePosition = querytext!!.indexOf(" ")
                        if (spacePosition > 0) {
                            querytext = querytext.substring(0, spacePosition)
                        }
                    }

                    // Makes an HTTP get request
                    // With the above client configuration, the resulting URL will be: https://owlbot.info/api/v4/dictionary/{querytext}
                    // {querytext} is an english word such that animal
                    val response: HttpResponse = client.get(querytext)

                    lateinit var word: String
                    lateinit var defLabel: String
                    lateinit var stringBody: String

                    if (response.status.value == 200) {

                        // https://stackoverflow.com/a/74233169
                        // Receives a raw body from server as String
                        stringBody = response.body()

                        // When the word definition is found, i.e stringBody does not contain "Error 404 (Not found)"
                        if (!stringBody.contains("Error 404 (Not found)", ignoreCase = true)) {

                            // Parses string to a JSON object
                            val theDocument: Map<String, JsonElement> =
                                Json.parseToJsonElement(stringBody).jsonObject

                            // "pronunciation"
                            val pronunciation: String = theDocument.get("pronunciation").toString()

                            // "word"
                            word = theDocument.get("word").toString()
                            // Trims the beginning and ending double quotes
                            word = word.substring(1, word.length - 1)

                            // "definitions"
                            val definitionArray = theDocument.get("definitions")?.jsonArray

                            for (i in definitionArray!!.indices) {
                                // JSON object at position i
                                val positionI = definitionArray[i].jsonObject

                                var type: String = positionI.get("type").toString()
                                // Trims the beginning and ending double quotes, capitalizes the first letter
                                type = type.substring(1, type.length - 1).capitalize()

                                var definition: String = positionI.get("definition").toString()
                                definition = definition.substring(1, definition.length - 1)

                                val example: String = positionI.get("example").toString()

                                // Initializes saveState to -1
                                var saveState = -1

                                // Checks whether the definition is the database, then set the value for saveState
                                val query =
                                    "Select * from " + MyContract.Entry.TABLE_NAME + " where " + MyContract.Entry.COLUMN_NAME_DEFINITION + " = \"" + definition + "\";"
                                val results = db?.rawQuery(query, null)

                                // Initializes saveState after querying the word definition
                                if (results != null) {
                                    // Sets 'saveState' to NOT_SAVED if the definition is not in the database
                                    if (results.count <= 0) {
                                        saveState = NOT_SAVED
                                    } else if (results.count >= 1) {
                                        saveState = SAVED
                                    }
                                }
                                results?.close()

                                // Initializes id to 0
//                        var id: Long = 0

                                // Adds the created wordDefinitions object to the ArrayList wordDefinitions
                                wordDefinitions.add(
                                    WordDefinition(
                                        word,
                                        pronunciation,
                                        type,
                                        definition,
                                        example,
                                        saveState,
//                                id
                                    )
                                )
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        // ui operation

                        // If the word definition is found
                        if (response.status.value == 200 &&
                            !stringBody.contains("Error 404 (Not found)", ignoreCase = true)
                        ) {
                            // Displays label "Definitions of ..." when word definition is found
                            defLabel = "Definitions of "

                            // Notifies the adapter object that the data set has changed.
                            adt!!.notifyDataSetChanged()
                        } else {
                            // When searching symbols, such as !@#$%^&*..., the status code is 200 but no definition is found
                            if (response.status.value == 404 ||
                                (response.status.value == 200 &&
                                        stringBody.contains(
                                            "Error 404 (Not found)",
                                            ignoreCase = true
                                        ))
                            ) {
                                word = "oops!  \"" + query + "\" not found"
                                defLabel = ""
                            } else if (response.status.value == 429) {
                                word = "Request was throttled.\nExpected available in 58 seconds."
                                defLabel = ""
                            }
                        }

                        // Makes it visible
                        wordLabel?.visibility = View.VISIBLE
                        definitionLabel?.visibility = View.VISIBLE

                        // Sets TextView
                        wordLabel?.text = word
                        definitionLabel?.text = defLabel

                        // Makes progress bar to be invisible when we have retrieved all the data
                        progressBar.visibility = View.INVISIBLE
                    }
                }
                return false
            }

            override fun onQueryTextChange(p0: String?): Boolean {
                return false
            }
        })
    }

    /* An alternative solution to make HTTP requests
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            val searchView: SearchView = binding.searchView

            // Instantiates the client by creating the HttpClient class instance
            val client = HttpClient()

            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {

                override fun onQueryTextSubmit(query: String?): Boolean {

                    // Adds a coroutine exception handler in launch code (able to log errors to Logcat)
                    uiScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
                        // asyncOperation

                        val stringURL = ("https://owlbot.info/api/v4/dictionary/"
                                + URLEncoder.encode( //convert all spaces in a string "query" to "+"
                            query,
                            "UTF-8"
                        ))

                        // Makes an HTTP get request
                        val response: HttpResponse =
                            client.get(stringURL) {
                                headers {
                                    append(
                                        HttpHeaders.Authorization,
                                        "Token **********************************"
                                    )
                                }
                            }

                        withContext(Dispatchers.Main) {
                            // ui operation
                            val textView: TextView = binding.textHome
                            textView.text = response.status.toString()
                        }
                    }
                    return false
                }

                override fun onQueryTextChange(p0: String?): Boolean {
                    return false
                }
            })
        }
    */

    /**
     * Provide a reference to the type of views that you are using.
     *
     * (create a class to represent a row. This is called the "ViewHolder", a class that
     * inherits from RecyclerView.ViewHolder)
     *
     * In the MyRowViews constructor, you are passed the View parameter, which is home_row.xml in
     * this case.
     */
    class MyRowViews(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val indexNumber: TextView
        val type: TextView
        val definition: TextView
        val favCheckBox: CheckBox

        // In Kotlin, the init block is used to initialize properties or execute code when an instance of a class is created
        init {
            indexNumber = itemView.findViewById(R.id.indexNumber)
            type = itemView.findViewById(R.id.type)
            definition = itemView.findViewById(R.id.definition)
            favCheckBox = itemView.findViewById(R.id.favCheckBox)

            // Defines click listener for the MyRowViews' itemView (i.e the listener is for when you click an item on the RecyclerView)
            itemView.setOnClickListener { _ ->
                val position = adapterPosition
                val thisDefinition = wordDefinitions[position]
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
                    .navigate(R.id.action_navigation_home_to_detailDefinitionFragment, bundle)
            }

            favCheckBox.setOnCheckedChangeListener { _, isChecked ->
                // The word definition position that you clicked on (i.e. the star icon position that you tap on)
                val position: Int = adapterPosition

                // Creates a new WordDefinition object when you click on the word definition
                val thisDefinition = wordDefinitions.get(position)

                // Checks whether the word definition is already in the database (prevent adding duplicates)
                val query =
                    "Select * from " + MyContract.Entry.TABLE_NAME + " where " + MyContract.Entry.COLUMN_NAME_DEFINITION + " = \"" + thisDefinition.definition + "\";"
                val results = db?.rawQuery(query, null)

                if (results != null) {
                    // If favCheckBox is checked and the word definition is not saved in the database, then add the word definition to the database
                    if (isChecked && results.count <= 0) {
                        // Sets the saveState to be SAVED
                        wordDefinitions.get(position).saveState = SAVED

                        // Sets what goes in the columns, you don't need to set the _ID column because the id increase automatically
                        val newRow = ContentValues().apply {
                            put(MyContract.Entry.COLUMN_NAME_WORD, thisDefinition.word)
                            put(
                                MyContract.Entry.COLUMN_NAME_PRONUNCIATION,
                                thisDefinition.pronunciation
                            )
                            put(MyContract.Entry.COLUMN_NAME_TYPE, thisDefinition.type)
                            put(MyContract.Entry.COLUMN_NAME_DEFINITION, thisDefinition.definition)
                            put(MyContract.Entry.COLUMN_NAME_EXAMPLE, thisDefinition.example)
                        }

                        // Inserts the new row to database
                        db?.insert(MyContract.Entry.TABLE_NAME, null, newRow)

                        // Inserts the new row to database, returning the primary key value of the new row, which is the _ID column
//                        val newRowId = db?.insert(MyContract.Entry.TABLE_NAME, null, newRow)

//                        if (newRowId != null) {
//                            thisDefinition.id = newRowId
//                        }
                    }
                    // If favCheckBox is not checked and the word definition is saved in the database, then delete the word definition from the database
                    else if (!isChecked && results.count >= 1) {
                        // Sets the saveState to be NOT_SAVED
                        wordDefinitions.get(position).saveState = NOT_SAVED

                        // Deletes the word definition from the database
                        db?.delete(
                            MyContract.Entry.TABLE_NAME,
                            "${MyContract.Entry.COLUMN_NAME_DEFINITION}=?",
                            arrayOf<String>(wordDefinitions.get(position).definition.toString())
                        )
                    }
                }
                results?.close()
            }
        }
    }

    class MyAdapter : RecyclerView.Adapter<MyRowViews>() {
        // Creates new views (invoked by the layout manager), i.e. creating a layout for a row
        // The parent parameter is the "RecyclerView"
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyRowViews {
            // Create a new view, which defines the UI of the list item
            val initRow = LayoutInflater.from(parent.context)
                .inflate(R.layout.home_row, parent, false)

            return MyRowViews(initRow)
        }

        // Replaces the contents of a view (invoked by the layout manager)
        override fun onBindViewHolder(holder: MyRowViews, position: Int) {
            // Initializes the state of 'favCheckBox' to be checked or unchecked

            // Checks whether the word definition is already in the database (prevent adding duplicates)
            val query =
                "Select * from " + MyContract.Entry.TABLE_NAME + " where " + MyContract.Entry.COLUMN_NAME_DEFINITION + " = \"" + wordDefinitions.get(
                    position
                ).definition + "\";"
            val results = db?.rawQuery(query, null)

            /**
             * Sets the state of 'favCheckBox' and 'saveState' based on the query on database,
             * because onViewCreated will be called every time while switching between home and
             * favorites fragments, rotating the screen, etc., which means the adapter will be
             * initialized again.
             *
             * So we have to retrieve the data from the database in case it is updated
             */
            if (results != null) {
                if (results.count >= 1) {
                    wordDefinitions.get(position).saveState == SAVED
                    holder.favCheckBox.isChecked = true
                } else if (results.count <= 0) {
                    wordDefinitions.get(position).saveState == NOT_SAVED
                    holder.favCheckBox.isChecked = false
                }
            }

            // holder.typeText.setText(wordDefinitions.get(position).getType()); in Java
            holder.type.text = wordDefinitions.get(position).type

            // Shows index starting from 1
            holder.indexNumber.text = (position + 1).toString()
            holder.definition.text = wordDefinitions.get(position).definition
        }

        override fun getItemCount(): Int {
            return wordDefinitions.size
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        job.cancel()

        // Closes the database
        db?.close()

        super.onDestroy()
    }

    // You can declare properties and initializes them in the primary constructor (either mutable (var) or read-only (val))
    // Without keyword 'val' or 'var', they are just parameters of the primary constructor instead of properties
    class WordDefinition(
        val word: String,
        val pronunciation: String,
        val type: String,
        val definition: String,
        val example: String,
        var saveState: Int, // 1 means the word definition has been saved in the database, 0 means not saved yet
    ) {
        // Declares and initializes property id
        var id: Long = 0

        // Secondary constructor
        constructor(
            word: String,
            pronunciation: String,
            type: String,
            definition: String,
            example: String,
            saveState: Int,
            id: Long
        ) : this(
            word,
            pronunciation,
            type,
            definition,
            example,
            saveState
        ) {
            this.id = id
        }
    }

    /**
     * Check whether a string contains more than one word. Returns true if it contains more than
     * one word, otherwise returns false.
     *
     * It trims any leading or trailing whitespace using the "trim" function.
     *
     * Then, it splits the string into an array of words using "split" function with a regular
     * expression pattern \s+ which matches one or more whitespace characters.
     *
     * Finally, the function checks if the size of the "words" array is greater than 1.  If it is,
     * then it means there are more than one word in the string, and the function returns true.
     * Otherwise, it returns false.
     *
     * (\s matches a single whitespace character, the plus sign + means one or more times.)
     *
     */
    fun hasMultipleWords(input: String): Boolean {
        val words = input.trim().split("\\s+".toRegex())
        return words.size > 1
    }
}