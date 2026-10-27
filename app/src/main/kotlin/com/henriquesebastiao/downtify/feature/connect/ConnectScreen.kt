package com.henriquesebastiao.downtify.feature.connect

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyLogo
import com.henriquesebastiao.downtify.core.designsystem.component.HeroHeader
import com.henriquesebastiao.downtify.core.designsystem.component.OnboardingFooter
import com.henriquesebastiao.downtify.core.designsystem.component.PhonePreviews
import com.henriquesebastiao.downtify.core.designsystem.component.ShapeIllustration
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyType
import com.henriquesebastiao.downtify.core.designsystem.theme.ListStyle
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.designsystem.theme.withDataValues
import com.henriquesebastiao.downtify.core.model.Capabilities
import com.henriquesebastiao.downtify.core.model.PairingPayload
import com.henriquesebastiao.downtify.core.model.ServerAddress
import com.henriquesebastiao.downtify.core.model.ServerInfo
import com.henriquesebastiao.downtify.core.network.discovery.DiscoveredServer

@Composable
fun ConnectRoute(
    pairingRequest: PairingPayload?,
    onPairingRequestHandled: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConnectViewModel = hiltViewModel(),
) {
    val flowState by viewModel.uiState.collectAsStateWithLifecycle()
    val state = flowState.copy(
        address = viewModel.address,
        code = viewModel.code,
        username = viewModel.username,
        password = viewModel.password,
    )
    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }
    var localNetworkDenied by remember { mutableStateOf(false) }
    val localNetwork = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        localNetworkDenied = !granted
        viewModel.startDiscovery()
    }
    LaunchedEffect(Unit) {
        // Android 17+ keeps apps off the home network without this; a typed
        // 192.168.x.x address, the stream and the WebSocket all need it.
        val needed = Build.VERSION.SDK_INT >= LOCAL_NETWORK_SDK &&
            ContextCompat.checkSelfPermission(context, ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED
        if (needed) localNetwork.launch(ACCESS_LOCAL_NETWORK) else viewModel.startDiscovery()
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        if (granted) viewModel.startScan()
    }

    LaunchedEffect(pairingRequest) {
        if (pairingRequest != null) {
            viewModel.onPairingPayload(pairingRequest)
            onPairingRequestHandled()
        }
    }

    if (state.scanning) {
        BackHandler { viewModel.stopScan() }
        QrScanner(onResult = viewModel::onScannedText, onClose = viewModel::stopScan, modifier = modifier)
        return
    }

    ConnectScreen(
        state = state,
        permissionDenied = permissionDenied,
        localNetworkDenied = localNetworkDenied,
        onAddressChange = viewModel::onAddressChange,
        onPickDiscovered = viewModel::onPickDiscovered,
        onConnect = viewModel::connect,
        onUsernameChange = viewModel::onUsernameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSignIn = viewModel::signIn,
        onUsePairingCode = viewModel::usePairingCode,
        onUsePassword = viewModel::usePassword,
        onCodeChange = viewModel::onCodeChange,
        onPair = viewModel::pair,
        onBackToAddress = viewModel::backToAddress,
        onScan = {
            permissionDenied = false
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            if (granted == PackageManager.PERMISSION_GRANTED) {
                viewModel.startScan()
            } else {
                cameraPermission.launch(Manifest.permission.CAMERA)
            }
        },
        modifier = modifier,
    )
}

/**
 * Two steps, an address and then the account's username and password (or a pairing code), laid out
 * as onboarding: a big title, a picture of the step, the form, and a footer whose 96 dp button
 * connects, signs in or pairs. It rises with the keyboard, so the action is in reach while typing.
 */
@Composable
fun ConnectScreen(
    state: ConnectUiState,
    onAddressChange: (String) -> Unit,
    onPickDiscovered: (DiscoveredServer) -> Unit,
    onConnect: () -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit,
    onUsePairingCode: () -> Unit,
    onUsePassword: () -> Unit,
    onCodeChange: (String) -> Unit,
    onPair: () -> Unit,
    onBackToAddress: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
    permissionDenied: Boolean = false,
    localNetworkDenied: Boolean = false,
) {
    val step = state.step
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    )
                    .padding(bottom = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedContent(
                    targetState = state.step,
                    transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
                    modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                    label = "connectStep",
                ) { shown ->
                    when (val step = shown) {
                        ConnectStep.Address ->
                            AddressStep(
                                state,
                                onAddressChange,
                                onPickDiscovered,
                                onConnect,
                                onScan,
                                permissionDenied,
                                localNetworkDenied,
                            )

                        is ConnectStep.SignIn ->
                            SignInStep(
                                state,
                                step,
                                onUsernameChange,
                                onPasswordChange,
                                onSignIn,
                                onUsePairingCode,
                                onBackToAddress,
                            )

                        is ConnectStep.Pair ->
                            PairStep(
                                state,
                                step,
                                onCodeChange,
                                onPair,
                                onUsePassword,
                                onBackToAddress,
                                onScan,
                                permissionDenied,
                            )
                    }
                }
            }
            val number = if (step is ConnectStep.Address) 1 else 2
            OnboardingFooter(
                step = number,
                label = stringResource(R.string.connect_step, number, STEPS),
                nextLabel = stringResource(
                    when (step) {
                        ConnectStep.Address -> R.string.connect_action
                        is ConnectStep.SignIn -> R.string.sign_in_action
                        is ConnectStep.Pair -> R.string.pair_action
                    },
                ),
                icon = if (step is ConnectStep.Address) DowntifyIcons.Forward else DowntifyIcons.Check,
                onNext = when (step) {
                    ConnectStep.Address -> onConnect
                    is ConnectStep.SignIn -> onSignIn
                    is ConnectStep.Pair -> onPair
                },
                enabled = when (step) {
                    ConnectStep.Address -> state.canConnect
                    is ConnectStep.SignIn -> state.canSignIn
                    is ConnectStep.Pair -> state.canPair
                },
                busy = state.busy,
            )
        }
    }
}

@Composable
private fun AddressStep(
    state: ConnectUiState,
    onAddressChange: (String) -> Unit,
    onPickDiscovered: (DiscoveredServer) -> Unit,
    onConnect: () -> Unit,
    onScan: () -> Unit,
    permissionDenied: Boolean,
    localNetworkDenied: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        HeroHeader(
            title = stringResource(R.string.connect_title_line1),
            accent = stringResource(R.string.connect_title_line2),
            subtitle = stringResource(R.string.connect_body),
            small = true,
        )
        // The ways in: the home network, the pairing code, the address.
        ShapeIllustration(
            satellites = listOf(DowntifyIcons.Wifi, DowntifyIcons.QrCode, DowntifyIcons.Server),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(horizontal = Spacing.xl),
        ) { DowntifyLogo(size = 64.dp) }

        Column(Modifier.padding(horizontal = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            if (state.revoked) Notice(stringResource(R.string.connect_revoked), DowntifyIcons.Unpair)
            if (localNetworkDenied) Notice(stringResource(R.string.connect_local_network_denied), DowntifyIcons.Warning)

            OutlinedTextField(
                value = state.address,
                onValueChange = onAddressChange,
                label = { Text(stringResource(R.string.connect_address_label)) },
                placeholder = { Text("http://192.168.1.20:8000", style = DowntifyType.dataInput) },
                leadingIcon = { Icon(painterResource(DowntifyIcons.Server), contentDescription = null) },
                supportingText = {
                    Text(errorText(state.error) ?: stringResource(R.string.connect_address_hint))
                },
                isError = state.error != null,
                singleLine = true,
                textStyle = DowntifyType.dataInput,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(onGo = { if (state.canConnect) onConnect() }),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.cleartextWarning) {
                Notice(
                    stringResource(R.string.connect_cleartext_warning),
                    DowntifyIcons.Warning,
                )
            }

            DiscoveredList(state, onPickDiscovered)

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ScanButton(stringResource(R.string.connect_scan), onScan, state.busy)
                if (permissionDenied) Notice(stringResource(R.string.scan_permission_denied), DowntifyIcons.Warning)
                if (state.error == ConnectError.NotAPairingCode || state.error == ConnectError.OtherServer) {
                    Notice(errorText(state.error).orEmpty(), DowntifyIcons.Error, error = true)
                }
            }
        }
    }
}

/** Servers that announce themselves on the network, as a segmented list to pick one from. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DiscoveredList(state: ConnectUiState, onPick: (DiscoveredServer) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(
                painterResource(DowntifyIcons.Wifi),
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                stringResource(R.string.connect_found),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.primary,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (state.discovered.isEmpty()) {
            Text(
                stringResource(R.string.connect_none_found),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        Column(
            Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(ListStyle.Segmented.gap),
        ) {
            val servers = state.discovered
            servers.forEachIndexed { index, server ->
                val selected = state.address.trimEnd('/') == server.baseUrl
                val shape = ListStyle.Segmented.shapeOf(index, servers.size)
                val subtitle = stringResource(R.string.connect_server_subtitle, server.address, server.version)
                SegmentedListItem(
                    selected = selected,
                    onClick = { onPick(server) },
                    shapes = ListItemShapes(shape, shape, shape, shape, shape, shape),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = scheme.surfaceContainer,
                        selectedContainerColor = scheme.primaryContainer,
                        selectedContentColor = scheme.onPrimaryContainer,
                        selectedSupportingContentColor = scheme.onPrimaryContainer,
                        selectedTrailingContentColor = scheme.onPrimaryContainer,
                    ),
                    leadingContent = { DowntifyLogo(size = 40.dp) },
                    supportingContent = { Text(withDataValues(subtitle, server.address, server.version)) },
                    trailingContent = { RadioButton(selected = selected, onClick = null) },
                    modifier = Modifier.semantics { role = Role.RadioButton },
                ) {
                    Text(server.name, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** The usual second step: the username and password the user signs in with on the web page. */
@Composable
private fun SignInStep(
    state: ConnectUiState,
    step: ConnectStep.SignIn,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit,
    onUsePairingCode: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var showPassword by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        StepHeader(
            title = stringResource(R.string.sign_in_title, step.info.name.ifBlank { step.baseUrl }),
            baseUrl = step.baseUrl,
            info = step.info,
            body = stringResource(R.string.sign_in_body),
        )
        // What signing in takes: the account, its password, the server it opens.
        ShapeIllustration(
            satellites = listOf(DowntifyIcons.Person, DowntifyIcons.Password, DowntifyIcons.Server),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(horizontal = Spacing.xl),
        ) {
            Icon(
                painterResource(DowntifyIcons.LockOpen),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
        }
        Column(Modifier.padding(horizontal = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            if (ServerAddress.isCleartextBeyondLan(step.baseUrl)) {
                Notice(stringResource(R.string.sign_in_cleartext_warning), DowntifyIcons.Warning)
            }
            OutlinedTextField(
                value = state.username,
                onValueChange = onUsernameChange,
                label = { Text(stringResource(R.string.sign_in_username)) },
                leadingIcon = { Icon(painterResource(DowntifyIcons.Person), contentDescription = null) },
                isError = state.error == ConnectError.WrongPassword,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username },
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.sign_in_password)) },
                leadingIcon = { Icon(painterResource(DowntifyIcons.Password), contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            painterResource(
                                if (showPassword) DowntifyIcons.VisibilityOff else DowntifyIcons.Visibility,
                            ),
                            contentDescription = stringResource(
                                if (showPassword) R.string.sign_in_hide_password else R.string.sign_in_show_password,
                            ),
                        )
                    }
                },
                supportingText = errorText(state.error)?.let { { Text(it) } },
                isError = state.error != null,
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (state.canSignIn) onSignIn() }),
                modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ScanButton(stringResource(R.string.sign_in_use_code), onUsePairingCode, state.busy)
                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.pair_other_server))
                }
            }
        }
    }
}

/** The way in without the password: a code (or its QR code) from the web page's Settings › Apps. */
@Composable
private fun PairStep(
    state: ConnectUiState,
    step: ConnectStep.Pair,
    onCodeChange: (String) -> Unit,
    onPair: () -> Unit,
    onUsePassword: () -> Unit,
    onBackToAddress: () -> Unit,
    onScan: () -> Unit,
    permissionDenied: Boolean,
) {
    val canUsePassword = step.info.capabilities.passwordSignIn
    BackHandler(onBack = if (canUsePassword) onUsePassword else onBackToAddress)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        StepHeader(
            title = stringResource(R.string.pair_title, step.info.name.ifBlank { step.baseUrl }),
            baseUrl = step.baseUrl,
            info = step.info,
            body = stringResource(R.string.pair_body),
        )
        // The ways to pair: the QR code, the typed code, the server it pairs with.
        ShapeIllustration(
            satellites = listOf(DowntifyIcons.QrCode, DowntifyIcons.Keyboard, DowntifyIcons.Server),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(horizontal = Spacing.xl),
        ) {
            Icon(
                painterResource(DowntifyIcons.LockOpen),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
        }
        Column(Modifier.padding(horizontal = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            OutlinedTextField(
                value = state.code,
                onValueChange = onCodeChange,
                label = { Text(stringResource(R.string.pair_code_label)) },
                placeholder = { Text(stringResource(R.string.pair_code_placeholder), style = DowntifyType.dataLarge) },
                leadingIcon = { Icon(painterResource(DowntifyIcons.Keyboard), contentDescription = null) },
                supportingText = errorText(state.error)?.let { { Text(it) } },
                isError = state.error != null,
                singleLine = true,
                textStyle = DowntifyType.dataLarge,
                visualTransformation = UppercaseTransformation,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(onDone = { if (state.canPair) onPair() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ScanButton(stringResource(R.string.pair_scan), onScan, state.busy)
                if (permissionDenied) Notice(stringResource(R.string.scan_permission_denied), DowntifyIcons.Warning)
                if (canUsePassword) {
                    TextButton(onClick = onUsePassword, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.sign_in_use_password))
                    }
                }
                TextButton(onClick = onBackToAddress, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.pair_other_server))
                }
            }
        }
    }
}

/** The second step's title, the server it talks to (address and version) and what to do. */
@Composable
private fun StepHeader(title: String, baseUrl: String, info: ServerInfo, body: String) {
    Column(
        Modifier.fillMaxWidth().padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        val address = baseUrl.substringAfter("://")
        Text(
            withDataValues(
                stringResource(R.string.connect_server_subtitle, address, info.version),
                address,
                info.version,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** The other way in, a 56 dp tonal button: the pairing code (or its QR code) from the web page. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScanButton(label: String, onClick: () -> Unit, busy: Boolean) {
    val height = ButtonDefaults.MediumContainerHeight
    FilledTonalButton(
        onClick = onClick,
        enabled = !busy,
        shapes = ButtonDefaults.shapesFor(height),
        contentPadding = ButtonDefaults.contentPaddingFor(height),
        modifier = Modifier.fillMaxWidth().heightIn(min = height),
    ) {
        Icon(
            painterResource(DowntifyIcons.QrCode),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
        )
        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
        Text(label, style = ButtonDefaults.textStyleFor(height))
    }
}

/** A message about the step: color, icon and words; an [error] in the error container. */
@Composable
private fun Notice(text: String, icon: Int, error: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = if (error) scheme.errorContainer else scheme.secondaryContainer,
        contentColor = if (error) scheme.onErrorContainer else scheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun errorText(error: ConnectError?): String? = when (error) {
    null -> null
    ConnectError.InvalidAddress -> stringResource(R.string.connect_error_invalid)
    ConnectError.Unreachable -> stringResource(R.string.connect_error_unreachable)
    ConnectError.NotDowntify -> stringResource(R.string.connect_error_not_downtify)
    is ConnectError.TooNew -> stringResource(R.string.connect_error_too_new, error.version)
    ConnectError.WrongCode -> stringResource(R.string.pair_error_wrong_code)
    ConnectError.WrongPassword -> stringResource(R.string.sign_in_error_wrong)
    ConnectError.SignInOff -> stringResource(R.string.sign_in_error_off)
    is ConnectError.SignInFailed -> stringResource(R.string.sign_in_error_failed, error.status)
    is ConnectError.TooManyAttempts -> stringResource(R.string.pair_error_too_many, error.secondsLeft)
    is ConnectError.PairFailed -> stringResource(R.string.pair_error_failed, error.status)
    ConnectError.OtherServer -> stringResource(R.string.pair_error_other_server)
    ConnectError.NotAPairingCode -> stringResource(R.string.scan_not_downtify)
}

private val previewServers = listOf(
    DiscoveredServer("nas", "cf12", "nas.local", "3.2.0", 1, "http://192.168.1.20:8000"),
    DiscoveredServer("pi", "ab34", "raspberrypi", "3.1.0", 1, "http://192.168.1.44:8000"),
)

@PhonePreviews
@Composable
private fun ConnectAddressPreview() {
    DowntifyTheme {
        ConnectScreen(
            state = ConnectUiState(
                address = "http://192.168.1.20:8000",
                discovered = previewServers,
                searching = false,
            ),
            onAddressChange = {},
            onPickDiscovered = {},
            onConnect = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onSignIn = {},
            onUsePairingCode = {},
            onUsePassword = {},
            onCodeChange = {},
            onPair = {},
            onBackToAddress = {},
            onScan = {},
        )
    }
}

private val previewInfo = ServerInfo(
    "cf12",
    "nas.local",
    "Downtify",
    "3.8.0",
    1,
    true,
    Capabilities(pairing = true, passwordSignIn = true),
)

@PhonePreviews
@Composable
private fun ConnectSignInPreview() {
    DowntifyTheme {
        ConnectScreen(
            state = ConnectUiState(
                step = ConnectStep.SignIn("http://192.168.1.20:8000", previewInfo),
                username = "maria",
                password = "correct horse",
                error = ConnectError.WrongPassword,
            ),
            onAddressChange = {},
            onPickDiscovered = {},
            onConnect = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onSignIn = {},
            onUsePairingCode = {},
            onUsePassword = {},
            onCodeChange = {},
            onPair = {},
            onBackToAddress = {},
            onScan = {},
        )
    }
}

@PhonePreviews
@Composable
private fun ConnectPairPreview() {
    DowntifyTheme {
        ConnectScreen(
            state = ConnectUiState(
                step = ConnectStep.Pair("http://192.168.1.20:8000", previewInfo),
                code = "K7QM-2XPD",
                error = ConnectError.WrongCode,
            ),
            onAddressChange = {},
            onPickDiscovered = {},
            onConnect = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onSignIn = {},
            onUsePairingCode = {},
            onUsePassword = {},
            onCodeChange = {},
            onPair = {},
            onBackToAddress = {},
            onScan = {},
        )
    }
}

private const val STEPS = 2
private const val LOCAL_NETWORK_SDK = 37
private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

/** Shows the pairing code uppercase without changing what the keyboard is composing. */
private val UppercaseTransformation = VisualTransformation { text ->
    // Identity offsets need the same length; a few letters (ß → SS) grow when uppercased.
    val upper = text.text.uppercase().takeIf { it.length == text.text.length } ?: text.text
    TransformedText(AnnotatedString(upper), OffsetMapping.Identity)
}
