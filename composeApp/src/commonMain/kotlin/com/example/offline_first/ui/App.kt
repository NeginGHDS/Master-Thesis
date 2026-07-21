package com.example.offline_first.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.example.offline_first.AppContainer
import com.example.offline_first.ui.navigation.Routes
import com.example.offline_first.ui.screens.addreport.AddReportScreen
import com.example.offline_first.ui.screens.home.ReportListScreen
import com.example.offline_first.ui.screens.login.LoginScreen
import com.example.offline_first.ui.screens.reportdetail.ReportDetailScreen
import com.example.offline_first.ui.screens.signup.SignUpScreen
import com.example.offline_first.viewmodel.AddReportViewModel
import com.example.offline_first.viewmodel.LoginViewModel
import com.example.offline_first.viewmodel.MainViewModel
import com.example.offline_first.viewmodel.ReportDetailViewModel
import com.example.offline_first.viewmodel.SignUpViewModel

/**
 * Platform UI seams that aren't expressible in shared Compose code: photo capture/picking and
 * Google Sign-In. Each platform's entry point ([com.example.offline_first.MainActivity] /
 * `MainViewController`) supplies the real implementation; the default no-ops here keep the shared
 * graph self-contained until Phase 5 wires them up.
 */
class PlatformActions(
    val pickPhotoFromGallery: (onResult: (String?) -> Unit) -> Unit = { it(null) },
    val takePhoto: (onResult: (String?) -> Unit) -> Unit = { it(null) },
    val signInWithGoogle: (onIdToken: (String?) -> Unit, onError: (String?) -> Unit) -> Unit =
        { _, onError -> onError("Google Sign-In is not available yet") }
)

@Composable
fun App(container: AppContainer, platformActions: PlatformActions = PlatformActions()) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val navController = rememberNavController()

            NavHost(navController = navController, startDestination = Routes.HOME) {
                composable(Routes.LOGIN) {
                    val viewModel = viewModel(key = "login") { LoginViewModel(container) }
                    LoginScreen(
                        viewModel = viewModel,
                        onNavigateToHome = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                            }
                        },
                        onNavigateToSignUp = { navController.navigate(Routes.SIGNUP) },
                        onGoogleSignInRequested = {
                            platformActions.signInWithGoogle(
                                { idToken -> if (idToken != null) viewModel.onGoogleIdTokenReceived(idToken) },
                                { error -> viewModel.onGoogleSignInFailed(error) }
                            )
                        }
                    )
                }

                composable(Routes.SIGNUP) {
                    val viewModel = viewModel(key = "signup") { SignUpViewModel(container) }
                    SignUpScreen(
                        viewModel = viewModel,
                        onSignedUp = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                            }
                        },
                        onBackToLogin = { navController.popBackStack() }
                    )
                }

                composable(Routes.HOME) {
                    val viewModel = viewModel(key = "main") { MainViewModel(container) }
                    ReportListScreen(
                        viewModel = viewModel,
                        onOpenReport = { reportId -> navController.navigate(Routes.reportDetail(reportId)) },
                        onAddReport = { navController.navigate(Routes.ADD_REPORT) },
                        onLoggedOut = {
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(Routes.HOME) { inclusive = true }
                            }
                        },
                        onSessionExpired = {
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(Routes.HOME) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Routes.ADD_REPORT) {
                    val viewModel = viewModel(key = "addReport") { AddReportViewModel(container) }
                    AddReportScreen(
                        viewModel = viewModel,
                        editingReportId = null,
                        onPickFromGallery = {
                            platformActions.pickPhotoFromGallery { path -> viewModel.setPhoto(path) }
                        },
                        onTakePhoto = {
                            platformActions.takePhoto { path -> viewModel.setPhoto(path) }
                        },
                        onSaved = { navController.popBackStack() },
                        onClosed = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Routes.EDIT_REPORT,
                    arguments = listOf(navArgument(Routes.ARG_REPORT_ID) { type = NavType.LongType })
                ) { backStackEntry ->
                    val reportId = backStackEntry.arguments?.read { getLongOrNull(Routes.ARG_REPORT_ID) } ?: -1L
                    val viewModel = viewModel(key = "editReport-$reportId") { AddReportViewModel(container) }
                    AddReportScreen(
                        viewModel = viewModel,
                        editingReportId = reportId,
                        onPickFromGallery = {
                            platformActions.pickPhotoFromGallery { path -> viewModel.setPhoto(path) }
                        },
                        onTakePhoto = {
                            platformActions.takePhoto { path -> viewModel.setPhoto(path) }
                        },
                        onSaved = { navController.popBackStack() },
                        onClosed = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Routes.REPORT_DETAIL,
                    arguments = listOf(navArgument(Routes.ARG_REPORT_ID) { type = NavType.LongType })
                ) { backStackEntry ->
                    val reportId = backStackEntry.arguments?.read { getLongOrNull(Routes.ARG_REPORT_ID) } ?: -1L
                    val viewModel = viewModel(key = "reportDetail-$reportId") { ReportDetailViewModel(container, reportId) }
                    ReportDetailScreen(
                        viewModel = viewModel,
                        onModifyReport = { id -> navController.navigate(Routes.editReport(id)) },
                        onClosed = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
