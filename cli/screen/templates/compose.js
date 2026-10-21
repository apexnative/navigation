// @ts-check
'use strict';

/**
 * A native screen in Jetpack Compose: a composable of `screen`, composed again when the props change.
 * @param {string} name such as `Profile`
 * @param {string} appPackage the package of the app's code
 * @param {string} definition where the screen's props are, for the comment
 * @returns {string}
 */
function composeScreen(name, appPackage, definition) {
  return `package ${appPackage}.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.apexnative.navigation.generated.NativeScreen
import dev.apexnative.navigation.generated.${name}Props
import kotlinx.coroutines.launch

/**
 * The native screen \`${name}\`. Its props (from ${definition}) and the navigation from it come in [screen]; it composes
 * again when the props change.
 */
@Composable
fun ${name}Screen(screen: NativeScreen<${name}Props>) {
  val scope = rememberCoroutineScope()
  MaterialTheme {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(screen.props.title ?: "${name}", style = MaterialTheme.typography.titleLarge)
      Button(onClick = { scope.launch { runCatching { screen.navigation.pop() } } }) { Text("Close") }
    }
  }
}
`;
}

module.exports = { composeScreen };
