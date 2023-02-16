package com.litao.niftyslider

import android.os.Bundle
import android.os.PersistableBundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import com.litao.niftyslider.databinding.ActivitySimpleBinding
import com.litao.niftyslider.fragment.M3StyleDemoFragment
import com.litao.niftyslider.fragment.ScrollContainerDemoFragment

/**
 * @author : litao
 * @date   : 2023/2/15 16:30
 */
class SimpleActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySimpleBinding

    companion object {
        const val EXTRA_ID = "id"
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivitySimpleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
    }

    private fun initView() {
        val id = intent.getIntExtra(EXTRA_ID, 0)

        val fragment = when (id) {
            Data.ID_1 -> {
                M3StyleDemoFragment.newInstance()
            }
            Data.ID_2 -> {
                ScrollContainerDemoFragment.newInstance()
            }
            else -> {
                null
            }
        }

        if (fragment != null) {
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragment_container_view, fragment)
                .commit()
        }
    }

}