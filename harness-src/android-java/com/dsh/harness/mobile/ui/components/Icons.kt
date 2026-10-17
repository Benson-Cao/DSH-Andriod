package com.dsh.harness.mobile.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 设计稿图标名 → Material Symbols 映射表。
 *
 * 设计稿用 Google Material Icons 的 ligature 名（class="material-icons">bolt<），
 * 这里转成 Compose 的 ImageVector。全稿共 56 个图标，都在此列出，
 * 避免组件里散落硬编码，也让"换图标库"只需改这一处。
 */
object DshIcons {

    // —— 导航与动作 ——
    val Menu = Icons.Filled.Menu
    val ArrowBack = Icons.Filled.ArrowBack
    val ArrowForward = Icons.Filled.ArrowForward
    val Close = Icons.Filled.Close
    val Check = Icons.Filled.Check
    val Add = Icons.Filled.Add
    val ExpandMore = Icons.Filled.ExpandMore
    val MoreHoriz = Icons.Filled.MoreHoriz
    val MoreVert = Icons.Filled.MoreVert
    val ChevronRight = Icons.Filled.KeyboardArrowRight
    val Search = Icons.Filled.Search
    val FilterList = Icons.Filled.FilterList
    val FileDownload = Icons.Filled.FileDownload
    val Settings = Icons.Filled.Settings
    val Lock = Icons.Filled.Lock
    val Info = Icons.Filled.Info
    val Warning = Icons.Filled.Warning
    val ErrorOutline = Icons.Outlined.ErrorOutline
    val Refresh = Icons.Filled.Refresh
    val CallSplit = Icons.Filled.CallSplit
    val Visibility = Icons.Filled.Visibility
    val VisibilityOff = Icons.Filled.VisibilityOff

    // —— 品牌与产品 ——
    val Bolt = Icons.Filled.Bolt
    val Science = Icons.Filled.Science
    val Tune = Icons.Filled.Tune
    val Build = Icons.Filled.Build
    val Psychology = Icons.Filled.Psychology
    val AutoAwesome = Icons.Filled.AutoAwesome
    val Done = Icons.Filled.Done
    val Draft = Icons.Outlined.EditNote
    val Power = Icons.Filled.Power
    val FilterNone = Icons.Filled.FilterNone
    val Subject = Icons.Filled.Subject

    // —— 文件与工作区 ——
    val Folder = Icons.Filled.Folder
    val FolderOpen = Icons.Filled.FolderOpen
    val HomeWork = Icons.Filled.HomeWork
    val CreateNewFolder = Icons.Outlined.CreateNewFolder
    val Description = Icons.Filled.Description
    val Article = Icons.Filled.Article
    val InsertDriveFile = Icons.Filled.InsertDriveFile
    val Code = Icons.Filled.Code
    val Extension = Icons.Filled.Extension
    val Slideshow = Icons.Filled.Slideshow
    val TableChart = Icons.Filled.TableChart
    val Image = Icons.Filled.Image
    val AddCircleOutline = Icons.Outlined.AddCircle
    val Public = Icons.Filled.Public
    val ImageSearch = Icons.Filled.ImageSearch
    val AddChart = Icons.Filled.AddChart

    // —— 存储与网络（NAS） ——
    val Cloud = Icons.Filled.Cloud
    val CloudDone = Icons.Filled.CloudDone
    val CloudOff = Icons.Filled.CloudOff
    val Dns = Icons.Filled.Dns
    val Storage = Icons.Filled.Storage
    val Sync = Icons.Filled.Sync
    val VpnKey = Icons.Filled.VpnKey
    val Smartphone = Icons.Filled.Smartphone
    val AccountTree = Icons.Filled.AccountTree

    // —— 设备与系统 ——
    val Wifi = Icons.Filled.Wifi
    val SignalCellularAlt = Icons.Filled.SignalCellularAlt
    val SignalCellularOff = Icons.Filled.SignalCellularOff
    val BatteryFull = Icons.Filled.BatteryFull
    val Palette = Icons.Filled.Palette
    val HelpOutline = Icons.Outlined.HelpOutline
    val Schedule = Icons.Filled.Schedule
    val Timeline = Icons.Filled.Timeline
    val Cached = Icons.Filled.Cached
    val SettingsInputComponent = Icons.Filled.SettingsInputComponent

    // —— 对话流（S5/S6/S8）——
    val Hub = Icons.Filled.Hub
    val CloudQueue = Icons.Filled.CloudQueue
    val Mic = Icons.Filled.Mic
    val Shield = Icons.Outlined.Shield
    val Stop = Icons.Filled.Stop
    val PlaylistAdd = Icons.Filled.PlaylistAdd
    val CallMerge = Icons.Filled.CallMerge
    val StopCircle = Icons.Filled.StopCircle
    val CheckCircle = Icons.Filled.CheckCircle
    val Share = Icons.Filled.Share
    val Difference = Icons.Filled.Difference
    val ArrowUpward = Icons.Filled.ArrowUpward
    val PlayArrow = Icons.Filled.PlayArrow
    val Rocket = Icons.Filled.Rocket

    // —— v11 插件生态（S12 / S19–S23）——
    val Storefront = Icons.Filled.Storefront
    val Explore = Icons.Filled.Explore
    val VerifiedUser = Icons.Filled.VerifiedUser
    val Star = Icons.Filled.Star
    val StarBorder = Icons.Outlined.StarBorder
    val Terminal = Icons.Filled.Terminal
    val DeleteOutline = Icons.Filled.DeleteOutline
    val FileCopy = Icons.Filled.FileCopy
    val SwapHoriz = Icons.Filled.SwapHoriz
    val BuildCircle = Icons.Filled.BuildCircle
    val Memory = Icons.Filled.Memory
    val Layers = Icons.Filled.Layers
    val Dashboard = Icons.Filled.Dashboard
    val Forum = Icons.Filled.Forum
    val Lan = Icons.Filled.Lan

    /** 允许调用方按名字动态取图标（S8 时间线那种用字符串的场合） */
    fun byName(name: String): ImageVector? = when (name) {
        "bolt" -> Bolt; "science" -> Science; "tune" -> Tune; "build" -> Build
        "description" -> Description; "code" -> Code; "extension" -> Extension
        "folder" -> Folder; "folder_open" -> FolderOpen; "home_work" -> HomeWork
        "cloud" -> Cloud; "cloud_done" -> CloudDone; "cloud_off" -> CloudOff
        "dns" -> Dns; "power" -> Icons.Filled.Power; "check" -> Check
        "close" -> Close; "menu" -> Menu; "arrow_back" -> ArrowBack
        "arrow_forward" -> ArrowForward; "expand_more" -> ExpandMore
        "chevron_right" -> ChevronRight; "lock" -> Lock; "info" -> Info
        "error_outline" -> ErrorOutline; "warning" -> Warning; "shield" -> Icons.Outlined.Shield
        "create_new_folder" -> CreateNewFolder; "add_circle_outline" -> AddCircleOutline
        "settings" -> Settings; "search" -> Search; "filter_list" -> FilterList
        "filter_none" -> FilterNone; "subject" -> Subject; "file_download" -> FileDownload
        "more_horiz" -> MoreHoriz; "more_vert" -> MoreVert; "done" -> Done
        "auto_awesome" -> AutoAwesome; "psychology" -> Psychology
        "account_tree" -> AccountTree; "add" -> Add; "add_chart" -> AddChart
        "article" -> Article; "image" -> Image; "image_search" -> ImageSearch
        "slideshow" -> Slideshow; "table_chart" -> TableChart; "smartphone" -> Smartphone
        "public" -> Public; "palette" -> Palette; "help_outline" -> HelpOutline
        "schedule" -> Schedule; "timeline" -> Timeline; "cached" -> Cached
        "repeat" -> Icons.Filled.Repeat; "call_split" -> CallSplit
        "settings_input_component" -> SettingsInputComponent; "vpn_key" -> VpnKey
        "draft" -> Draft; "visibility" -> Visibility; "zoom_in" -> Icons.Filled.ZoomIn
        else -> null
    }
}