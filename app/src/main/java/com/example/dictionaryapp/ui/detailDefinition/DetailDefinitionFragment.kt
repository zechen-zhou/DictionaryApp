package com.example.dictionaryapp.ui.detailDefinition

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.dictionaryapp.R
import com.example.dictionaryapp.databinding.FragmentDetailDefinitionBinding

class DetailDefinitionFragment : Fragment() {

    private var _binding: FragmentDetailDefinitionBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    // Delays the initialization (late initialization) of a non-null variable
    private lateinit var word: TextView
    private lateinit var pronunciation: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adt: MyDetailDefinitionAdapter

    // companion object is used to define static members and methods associated with a class
    companion object {
        lateinit var keyList: ArrayList<String>
        lateinit var bundle: Bundle
        lateinit var myMap: Map<String, String>
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {

        // Inflates the layout for this fragment
        _binding = FragmentDetailDefinitionBinding.inflate(inflater, container, false)
        val root: View = binding.root

        word = binding.word
        pronunciation = binding.pronunciation
        recyclerView = binding.recyclerView

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Adds dividers and space between items in RecyclerView (https://stackoverflow.com/a/41201865)
        recyclerView.addItemDecoration(
            DividerItemDecoration(
                getContext(),
                DividerItemDecoration.VERTICAL
            )
        )

        bundle = this.requireArguments()
        if (bundle != null) {
            keyList = bundle.getStringArrayList("keyList")!!
            myMap = bundle.getSerializable("myMap") as Map<String, String>

            word.text = bundle.getString("word")

            if (bundle.getString("pronunciation") != null) {
                pronunciation.text = bundle.getString("pronunciation")
            } else {
                pronunciation.visibility = View.INVISIBLE
            }
        }

        adt = MyDetailDefinitionAdapter()
        recyclerView.adapter = adt

        // Disable RecyclerView Items from clicking
        recyclerView.isClickable = false

        recyclerView.layoutManager = LinearLayoutManager(context)
    }

    class MyDetailDefinitionRowViews(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView
        val content: TextView

        init {
            title = itemView.findViewById(R.id.title)
            content = itemView.findViewById(R.id.content)
        }
    }

    class MyDetailDefinitionAdapter : RecyclerView.Adapter<MyDetailDefinitionRowViews>() {
        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): MyDetailDefinitionRowViews {

            val initRow = LayoutInflater.from(parent.context)
                .inflate(R.layout.detail_definition_row, parent, false)

            return MyDetailDefinitionRowViews(initRow)
        }

        override fun onBindViewHolder(holder: MyDetailDefinitionRowViews, position: Int) {
            // Trims the beginning and ending double quotes for "example" only
            if (keyList[position] == "example") {
                var content = myMap?.get(keyList[position])

                // Trims the beginning and ending double quotes
                content = content!!.substring(1, content.length - 1)

                // Capitalizes the first letter
                holder.title.text = keyList[position].capitalize()
                holder.content.text = content.capitalize()
            } else {
                holder.title.text = keyList[position].capitalize()
                holder.content.text = myMap?.get(keyList[position])?.capitalize()
            }
        }

        override fun getItemCount(): Int {
            return keyList.size
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}