package com.khizrim.dialer.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.khizrim.dialer.DialerViewModel
import com.khizrim.dialer.model.CommMethod
import com.khizrim.dialer.model.Contact
import com.khizrim.dialer.model.IncomingScenario
import com.khizrim.dialer.ui.components.MethodPickerSheet
import com.khizrim.dialer.ui.components.PickerMode
import com.khizrim.dialer.ui.contact.ContactDetailScreen
import com.khizrim.dialer.ui.contacts.ContactsScreen
import com.khizrim.dialer.ui.incoming.IncomingCallScreen
import com.khizrim.dialer.ui.keypad.KeypadScreen
import com.khizrim.dialer.ui.recents.RecentsScreen
import kotlinx.coroutines.launch

private enum class Tab(val title: String, val icon: ImageVector) {
    CONTACTS("Контакты", Icons.Rounded.People),
    RECENTS("Недавние", Icons.Rounded.History),
    KEYPAD("Клавиатура", Icons.Rounded.Dialpad),
}

/** Контакт, который звонит в демо-сценарии «Звонит контакт». */
private const val DEMO_CALLER_ID = 3L

@Composable
fun DialerApp(vm: DialerViewModel = viewModel()) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "main") {
        composable("main") {
            MainScreen(
                vm = vm,
                onOpenContact = { nav.navigate("contact/${it.id}") },
                onSimulateIncoming = { nav.navigate("incoming/${it.name}") },
            )
        }
        composable(
            "contact/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            val contact = vm.contact(id) ?: return@composable
            ContactDetailScreen(
                contact = contact,
                onBack = { nav.popBackStack() },
                onSetDefault = { vm.setDefaultMethod(id, it) },
                onToggleFavorite = { vm.toggleFavorite(id) },
            )
        }
        composable(
            "incoming/{scenario}",
            arguments = listOf(navArgument("scenario") { type = NavType.StringType }),
            enterTransition = { slideInVertically { it } + fadeIn() },
            exitTransition = { slideOutVertically { it } + fadeOut() },
        ) { entry ->
            val scenario = IncomingScenario.valueOf(entry.arguments?.getString("scenario") ?: IncomingScenario.CONTACT.name)
            IncomingCallScreen(
                scenario = scenario,
                contact = if (scenario == IncomingScenario.CONTACT) vm.contact(DEMO_CALLER_ID) else null,
                onClose = { nav.popBackStack("main", inclusive = false) },
            )
        }
    }
}

@Composable
private fun MainScreen(
    vm: DialerViewModel,
    onOpenContact: (Contact) -> Unit,
    onSimulateIncoming: (IncomingScenario) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.CONTACTS) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var oneTimePickerFor by remember { mutableStateOf<Contact?>(null) }

    fun announce(text: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }

    fun perform(contact: Contact, method: CommMethod) = announce("${method.title}: ${contact.name} (демо)")

    fun quickAction(contact: Contact) {
        val method = contact.defaultMethod
        if (method != null) perform(contact, method) else oneTimePickerFor = contact
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, null) },
                        label = { Text(item.title) },
                    )
                }
            }
        },
    ) { padding: PaddingValues ->
        when (tab) {
            Tab.CONTACTS -> ContactsScreen(
                contacts = vm.contacts,
                onOpenContact = onOpenContact,
                onQuickAction = ::quickAction,
                onSimulateIncoming = onSimulateIncoming,
                contentPadding = padding,
            )
            Tab.RECENTS -> RecentsScreen(
                recents = vm.recents,
                contactById = vm::contact,
                onOpenContact = onOpenContact,
                onCallBack = { call, contact ->
                    announce("${call.method.title}: ${contact?.name ?: call.number} (демо)")
                },
                contentPadding = padding,
            )
            Tab.KEYPAD -> KeypadScreen(
                contacts = vm.contacts,
                onCall = { number, contact -> announce("Звонок: ${contact?.name ?: number} (демо)") },
                onOpenContact = onOpenContact,
                contentPadding = padding,
            )
        }
    }

    oneTimePickerFor?.let { contact ->
        MethodPickerSheet(
            contact = contact,
            mode = PickerMode.ONE_TIME,
            onDismiss = { oneTimePickerFor = null },
            onPick = { method ->
                oneTimePickerFor = null
                method?.let { perform(contact, it) }
            },
        )
    }
}
