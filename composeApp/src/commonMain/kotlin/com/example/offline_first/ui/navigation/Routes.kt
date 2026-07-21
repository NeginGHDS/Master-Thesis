package com.example.offline_first.ui.navigation

object Routes {
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val HOME = "home"
    const val ADD_REPORT = "addReport"
    const val EDIT_REPORT = "editReport/{reportId}"
    const val REPORT_DETAIL = "reportDetail/{reportId}"

    const val ARG_REPORT_ID = "reportId"

    fun editReport(reportId: Long) = "editReport/$reportId"
    fun reportDetail(reportId: Long) = "reportDetail/$reportId"
}
