package okoge.house.throttling_app.testapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import okoge.house.throttling_app.testapp.ui.TestDashboard
import okoge.house.throttling_app.testapp.ui.theme.TestAppTheme

class MainActivity : ComponentActivity() {

    private var tester: NetworkTester? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val networkTester = NetworkTester(lifecycleScope)
        tester = networkTester

        setContent {
            TestAppTheme {
                val state by networkTester.state.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TestDashboard(
                        state = state,
                        onStartStop = {
                            if (state.isRunning) {
                                networkTester.stop()
                            } else {
                                networkTester.start()
                            }
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tester?.stop()
    }
}
