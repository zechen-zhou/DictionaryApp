package com.example.dictionaryapp.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.dictionaryapp.BuildConfig
import com.example.dictionaryapp.R
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

    companion object {
        val wordDefinitions = ArrayList<WordDefinition>()
        var searchView: SearchView? = null
        var wordLabel: TextView? = null
        var definitionLabel: TextView? = null
        var recyclerView: RecyclerView? = null
    }

    val adt = MyAdapter()

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

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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

        // recyclerView.setAdapter(adt); in Java
        recyclerView?.adapter = adt
        recyclerView!!.layoutManager = LinearLayoutManager(context)

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

                // Adds a coroutine exception handler in launch code (able to log errors to Logcat)
                uiScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
                    // asyncOperation

                    // Clears the last search results
                    wordDefinitions.clear()

                    // Makes an HTTP get request
                    // With the above client configuration, the resulting URL will be: https://owlbot.info/api/v4/dictionary/{query}
                    // {query} is an english word such that animal
                    val response: HttpResponse = client.get(
                        //convert all spaces in a string "query" to "+"
                        URLEncoder.encode(
                            query, "UTF-8"
                        )
                    )

                    // https://stackoverflow.com/a/74233169
                    // Receives a raw body from server as String
                    val stringBody: String = response.body()

                    // Parses string to a JSON object
                    val theDocument: Map<String, JsonElement> =
                        Json.parseToJsonElement(stringBody).jsonObject

                    // "pronunciation"
                    val pronunciation: String = theDocument.get("pronunciation").toString()

                    // "word"
                    var word: String = theDocument.get("word").toString()
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

                        // Adds the created wordDefinitions object to the ArrayList wordDefinitions
                        wordDefinitions.add(
                            WordDefinition(
                                word,
                                pronunciation,
                                type,
                                definition,
                                example
                            )
                        )
                    }

                    withContext(Dispatchers.Main) {
                        // ui operation

                        // Displays label "Definitions of ..." when query is changed
                        wordLabel?.text = word
                        definitionLabel?.text = "Definitions of "

                        // Notifies the adapter object that the data set has changed.
                        adt.notifyDataSetChanged()
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
     * In the MyRowViews constructor, you are passed the View parameter, which is home_row.xml in
     * this case.
     */
    class MyRowViews(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val indexNumber: TextView
        val type: TextView
        val definition: TextView

        // Initialization code can be placed in initializer blocks prefixed with the init keyword
        init {
            indexNumber = itemView.findViewById(R.id.indexNumber)
            type = itemView.findViewById(R.id.type)
            definition = itemView.findViewById(R.id.definition)
        }
    }

    class MyAdapter : RecyclerView.Adapter<MyRowViews>() {
        // Creates new views (invoked by the layout manager)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyRowViews {
            // Create a new view, which defines the UI of the list item
            val initRow = LayoutInflater.from(parent.context)
                .inflate(R.layout.home_row, parent, false)

            return MyRowViews(initRow)
        }

        // Replaces the contents of a view (invoked by the layout manager)
        override fun onBindViewHolder(holder: MyRowViews, position: Int) {
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
        super.onDestroy()
    }

    // You can declare properties and initializes them in the primary constructor (either mutable (var) or read-only (val))
    class WordDefinition(
        val word: String,
        val pronunciation: String,
        val type: String,
        val definition: String,
        val example: String,
    )
}