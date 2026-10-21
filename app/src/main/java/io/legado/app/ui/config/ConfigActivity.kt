package io.legado.app.ui.config

import android.widget.ScrollView
import android.widget.TextView
import io.legado.app.constant.PreferKey
import io.legado.app.help.adaptive.AdaptiveUiController
import io.legado.app.help.adaptive.AdaptivePanePolicy
import io.legado.app.help.adaptive.AdaptiveSettingsRow
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.uiTypeface
import io.legado.app.utils.observeSharedPreferences
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout.LayoutParams as FrameLayoutParams
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.EventBus
import io.legado.app.ui.widget.compose.rememberAppSettingPalette
import io.legado.app.utils.dpToPx
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import androidx.viewbinding.ViewBinding

private const val CONFIG_MENU_HOST_WIDTH_DP = 128

class ConfigActivity : VMBaseActivity<ViewBinding, ConfigViewModel>() {

    private lateinit var adaptiveBody: LinearLayout
    private lateinit var adaptiveNavigation: ScrollView
    private lateinit var adaptiveDivider: View
    private lateinit var navigationRows: LinearLayout
    private lateinit var titleComposeView: ComposeView
    private lateinit var menuToolbar: Toolbar
    private var titleText by mutableStateOf("")

    override val binding: ViewBinding by lazy {
        titleText = getString(R.string.setting)
        titleComposeView = ComposeView(this)
        menuToolbar = Toolbar(this).apply {
            title = ""
            subtitle = ""
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setContentInsetsAbsolute(0, 0)
            contentInsetStartWithNavigation = 0
            contentInsetEndWithActions = 0
        }
        val topBarHost = FrameLayout(this).apply {
            addView(
                titleComposeView,
                FrameLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                menuToolbar,
                FrameLayoutParams(
                    CONFIG_MENU_HOST_WIDTH_DP.dpToPx(),
                    56.dpToPx(),
                    Gravity.END or Gravity.BOTTOM
                )
            )
            setOnApplyWindowInsetsListenerCompat { _, windowInsets ->
                val top = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                (menuToolbar.layoutParams as? FrameLayoutParams)?.let { params ->
                    if (params.topMargin != top) {
                        params.topMargin = top
                        menuToolbar.layoutParams = params
                    }
                }
                windowInsets
            }
        }
        navigationRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        adaptiveNavigation = ScrollView(this).apply {
            isFillViewport = true
            visibility = View.GONE
            setBackgroundColor(this@ConfigActivity.bottomBackground)
            addView(navigationRows)
        }
        adaptiveDivider = View(this).apply {
            visibility = View.GONE
            setBackgroundColor(0x22888888)
        }
        adaptiveBody = AdaptiveSettingsRow(this).apply {
            addView(adaptiveNavigation, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            addView(adaptiveDivider, LinearLayout.LayoutParams(1.dpToPx(), ViewGroup.LayoutParams.MATCH_PARENT))
            addView(FrameLayout(this@ConfigActivity).apply { id = R.id.configFrameLayout },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(
                topBarHost,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                adaptiveBody,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
        }
        object : ViewBinding {
            override fun getRoot(): View = root
        }
    }
    override val viewModel by viewModels<ConfigViewModel>()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        setSupportActionBar(menuToolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        titleComposeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        titleComposeView.setContent {
            ConfigTopBar(
                title = titleText,
                onBack = ::supportFinishAfterTransition
            )
        }
        when (val configTag = intent.getStringExtra("configTag")) {
            ConfigTag.OTHER_CONFIG -> replaceFragment(configTag, OtherConfigFragment::class.java)
            ConfigTag.NETWORK_DNS_CONFIG -> replaceFragment(configTag, NetworkDnsConfigFragment::class.java)
            ConfigTag.THEME_CONFIG -> replaceFragment(configTag, ThemeConfigFragment::class.java)
            ConfigTag.BACKUP_CONFIG -> replaceFragment(configTag, BackupConfigFragment::class.java)
            ConfigTag.AI_CONFIG -> replaceFragment(configTag, AiConfigFragment::class.java)
            ConfigTag.COVER_CONFIG -> replaceFragment(configTag, CoverConfigFragment::class.java)
            ConfigTag.WELCOME_CONFIG -> replaceFragment(configTag, WelcomeConfigFragment::class.java)
            ConfigTag.DISCOVERY_SUBSCRIPTION_CONFIG ->
                replaceFragment(configTag, DiscoverySubscriptionConfigFragment::class.java)
            ConfigTag.DISCOVERY_CONFIG -> replaceFragment(configTag, DiscoveryConfigFragment::class.java)
            ConfigTag.SUBSCRIPTION_CONFIG -> replaceFragment(configTag, SubscriptionConfigFragment::class.java)
            else -> finish()
        }
        setupAdaptiveNavigation()
        adaptiveBody.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) updateAdaptiveLayout()
        }
        adaptiveBody.post { updateAdaptiveLayout() }
        observeSharedPreferences { _, key ->
            if (key == PreferKey.tabletFoldOptimization) adaptiveBody.post { updateAdaptiveLayout() }
        }

    }

    private fun updateAdaptiveLayout() {
        val usePanes = AdaptivePanePolicy.useTwoPanes(
            AdaptiveUiController.enabled(this),
            (adaptiveBody.width / resources.displayMetrics.density).toInt(),
            resources.configuration.screenHeightDp
        ) && !AdaptiveUiController.embedded(this)
        val target = if (usePanes) View.VISIBLE else View.GONE
        if (adaptiveNavigation.visibility != target) {
            adaptiveNavigation.visibility = target
            adaptiveDivider.visibility = target
        }
    }

    private fun setupAdaptiveNavigation() {
        val pages: List<Triple<String, String, Class<out Fragment>>> = listOf(
            Triple("界面设置", ConfigTag.THEME_CONFIG, ThemeConfigFragment::class.java),
            Triple("AI 设置", ConfigTag.AI_CONFIG, AiConfigFragment::class.java),
            Triple("网络与 DNS", ConfigTag.NETWORK_DNS_CONFIG, NetworkDnsConfigFragment::class.java),
            Triple("备份与恢复", ConfigTag.BACKUP_CONFIG, BackupConfigFragment::class.java),
            Triple("封面设置", ConfigTag.COVER_CONFIG, CoverConfigFragment::class.java),
            Triple("发现设置", ConfigTag.DISCOVERY_CONFIG, DiscoveryConfigFragment::class.java),
            Triple("订阅设置", ConfigTag.SUBSCRIPTION_CONFIG, SubscriptionConfigFragment::class.java),
            Triple("其他设置", ConfigTag.OTHER_CONFIG, OtherConfigFragment::class.java)
        )
        navigationRows.removeAllViews()
        pages.forEach { (label, tag, fragmentClass) ->
            navigationRows.addView(TextView(this).apply {
                text = label
                textSize = 16f
                typeface = this@ConfigActivity.uiTypeface()
                gravity = Gravity.CENTER_VERTICAL
                minHeight = 64.dpToPx()
                setPadding(20.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
                setTextColor(if (intent.getStringExtra("configTag") == tag) this@ConfigActivity.primaryColor
                    else this@ConfigActivity.primaryTextColor)
                setOnClickListener {
                    replaceFragment(tag, fragmentClass)
                    setupAdaptiveNavigation()
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    override fun setTitle(resId: Int) {
        super.setTitle(resId)
        titleText = getString(resId)
    }

    override fun setTitle(title: CharSequence?) {
        super.setTitle(title)
        titleText = title?.toString().orEmpty()
    }

    fun <T : Fragment> replaceFragment(configTag: String, fragmentClass: Class<T>) {
        intent.putExtra("configTag", configTag)
        val configFragment = supportFragmentManager.findFragmentByTag(configTag)
            ?: fragmentClass.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.configFrameLayout, configFragment, configTag)
            .commit()
    }

    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<String>(EventBus.RECREATE) {
            recreate()
        }
    }

}

@Composable
private fun ConfigTopBar(
    title: String,
    onBack: () -> Unit
) {
    val palette = rememberAppSettingPalette()
    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = palette.bodyFontFamily)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = null,
                    tint = palette.primaryText
                )
            }
            Text(
                text = title,
                color = palette.primaryText,
                fontFamily = palette.titleFontFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(CONFIG_MENU_HOST_WIDTH_DP.dp))
        }
    }
}
