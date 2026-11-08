package com.billpro.app.ui.auth

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.billpro.app.auth.AuthResult
import com.billpro.app.auth.AuthService
import com.billpro.app.auth.UserSession
import com.billpro.app.ui.BillProTheme
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    authService: AuthService,
    onAuthSuccess: (UserSession) -> Unit
) {
    var isRegisterMode by remember { mutableStateOf(false) }
    var isForgotPasswordOpen by remember { mutableStateOf(false) }

    if (isRegisterMode) {
        RegisterScreen(
            authService = authService,
            onSuccess = onAuthSuccess,
            onNavigateToLogin = { isRegisterMode = false }
        )
    } else {
        LoginScreen(
            authService = authService,
            onSuccess = onAuthSuccess,
            onNavigateToRegister = { isRegisterMode = true },
            onForgotPasswordClick = { isForgotPasswordOpen = true }
        )
    }

    if (isForgotPasswordOpen) {
        ForgotPasswordDialog(
            authService = authService,
            onDismiss = { isForgotPasswordOpen = false }
        )
    }
}

@Composable
fun LoginScreen(
    authService: AuthService,
    onSuccess: (UserSession) -> Unit,
    onNavigateToRegister: () -> Unit,
    onForgotPasswordClick: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BillProTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Logo & Branding Header
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(BillProTheme.colors.accentGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PointOfSale,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = "BillPro Multi-Business",
            color = BillProTheme.colors.text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Sign in to manage your business billing & finances",
            color = BillProTheme.colors.muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
        )

        // Login Card
        Surface(
            color = BillProTheme.colors.surface,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BillProTheme.colors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Email field
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        errorMessage = null
                    },
                    label = { Text("Business Email") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Email,
                            contentDescription = null,
                            tint = BillProTheme.colors.accent
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BillProTheme.colors.accent,
                        unfocusedBorderColor = BillProTheme.colors.cardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Password field
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Password") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = BillProTheme.colors.accent
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                                tint = BillProTheme.colors.muted
                            )
                        }
                    },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BillProTheme.colors.accent,
                        unfocusedBorderColor = BillProTheme.colors.cardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Remember Me & Forgot Password Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { rememberMe = !rememberMe }
                    ) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = { rememberMe = it },
                            colors = CheckboxDefaults.colors(checkedColor = BillProTheme.colors.accent)
                        )
                        Text(
                            text = "Remember me",
                            fontSize = 12.sp,
                            color = BillProTheme.colors.textSecondary
                        )
                    }

                    Text(
                        text = "Forgot Password?",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BillProTheme.colors.accent,
                        modifier = Modifier
                            .clickable { onForgotPasswordClick() }
                            .padding(4.dp)
                    )
                }

                if (errorMessage != null) {
                    Surface(
                        color = BillProTheme.colors.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = BillProTheme.colors.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = errorMessage ?: "",
                                color = BillProTheme.colors.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Submit Login Button
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        if (email.isBlank() || password.isBlank()) {
                            errorMessage = "Please enter both email and password."
                            return@Button
                        }
                        isLoading = true
                        errorMessage = null
                        coroutineScope.launch {
                            val result = authService.login(email, password)
                            isLoading = false
                            when (result) {
                                is AuthResult.Success -> {
                                    Toast.makeText(context, "Welcome back, ${result.session.ownerName}!", Toast.LENGTH_SHORT).show()
                                    onSuccess(result.session)
                                }
                                is AuthResult.Error -> {
                                    errorMessage = result.message
                                }
                            }
                        }
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BillProTheme.colors.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Sign In",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Switch to Register Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Don't have an account?",
                color = BillProTheme.colors.muted,
                fontSize = 13.sp
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Register Business",
                color = BillProTheme.colors.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { onNavigateToRegister() }
                    .padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
fun RegisterScreen(
    authService: AuthService,
    onSuccess: (UserSession) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    var ownerName by remember { mutableStateOf("") }
    var businessName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BillProTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Icon
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(BillProTheme.colors.accentGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AppRegistration,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "Register New Business",
            color = BillProTheme.colors.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Create an isolated account for your store or enterprise",
            color = BillProTheme.colors.muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 22.dp)
        )

        // Registration Form Card
        Surface(
            color = BillProTheme.colors.surface,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BillProTheme.colors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Owner Name
                OutlinedTextField(
                    value = ownerName,
                    onValueChange = { ownerName = it; errorMessage = null },
                    label = { Text("Owner Full Name *") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, tint = BillProTheme.colors.accent)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Business Name
                OutlinedTextField(
                    value = businessName,
                    onValueChange = { businessName = it; errorMessage = null },
                    label = { Text("Business / Store Name *") },
                    leadingIcon = {
                        Icon(Icons.Default.Storefront, contentDescription = null, tint = BillProTheme.colors.accent)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Email
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMessage = null },
                    label = { Text("Email Address *") },
                    leadingIcon = {
                        Icon(Icons.Default.Email, contentDescription = null, tint = BillProTheme.colors.accent)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Phone
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; errorMessage = null },
                    label = { Text("Phone Number *") },
                    leadingIcon = {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = BillProTheme.colors.accent)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Password
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null },
                    label = { Text("Password (min 6 chars) *") },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = BillProTheme.colors.accent)
                    },
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = BillProTheme.colors.muted
                            )
                        }
                    },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Confirm Password
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; errorMessage = null },
                    label = { Text("Confirm Password *") },
                    leadingIcon = {
                        Icon(Icons.Default.LockReset, contentDescription = null, tint = BillProTheme.colors.accent)
                    },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Surface(
                        color = BillProTheme.colors.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = BillProTheme.colors.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = errorMessage ?: "",
                                color = BillProTheme.colors.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Register Button
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        if (ownerName.isBlank() || businessName.isBlank() || email.isBlank() || phone.isBlank()) {
                            errorMessage = "Please fill in all required fields."
                            return@Button
                        }
                        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
                            errorMessage = "Please enter a valid email address."
                            return@Button
                        }
                        if (password.length < 6) {
                            errorMessage = "Password must be at least 6 characters long."
                            return@Button
                        }
                        if (password != confirmPassword) {
                            errorMessage = "Passwords do not match."
                            return@Button
                        }

                        isLoading = true
                        errorMessage = null
                        coroutineScope.launch {
                            val result = authService.register(
                                ownerName = ownerName,
                                businessName = businessName,
                                email = email,
                                phone = phone,
                                password = password
                            )
                            isLoading = false
                            when (result) {
                                is AuthResult.Success -> {
                                    Toast.makeText(
                                        context,
                                        "Registered! Business ID: ${result.session.businessId}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onSuccess(result.session)
                                }
                                is AuthResult.Error -> {
                                    errorMessage = result.message
                                }
                            }
                        }
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BillProTheme.colors.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Create Business Account",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Return to Login
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Already have an account?",
                color = BillProTheme.colors.muted,
                fontSize = 13.sp
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Sign In",
                color = BillProTheme.colors.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { onNavigateToLogin() }
                    .padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
fun ForgotPasswordDialog(
    authService: AuthService,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var resetEmail by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Reset Password",
                fontWeight = FontWeight.Bold,
                color = BillProTheme.colors.text
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Enter your registered business email. We'll send instructions to reset your password.",
                    fontSize = 13.sp,
                    color = BillProTheme.colors.muted
                )

                OutlinedTextField(
                    value = resetEmail,
                    onValueChange = { resetEmail = it; message = null },
                    label = { Text("Business Email") },
                    leadingIcon = {
                        Icon(Icons.Default.Email, contentDescription = null, tint = BillProTheme.colors.accent)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (message != null) {
                    Text(
                        text = message ?: "",
                        fontSize = 12.sp,
                        color = if (isSuccess) BillProTheme.colors.success else BillProTheme.colors.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(resetEmail.trim()).matches()) {
                        message = "Please enter a valid email address."
                        isSuccess = false
                        return@Button
                    }
                    isSubmitting = true
                    coroutineScope.launch {
                        val result = authService.sendPasswordReset(resetEmail.trim())
                        isSubmitting = false
                        result.fold(
                            onSuccess = {
                                isSuccess = true
                                message = "Password reset email sent! Check your inbox."
                                Toast.makeText(context, "Password reset email dispatched", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = {
                                isSuccess = false
                                message = it.localizedMessage ?: "Failed to send reset email."
                            }
                        )
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = BillProTheme.colors.accent)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Send Reset Link")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = BillProTheme.colors.muted)
            }
        }
    )
}
