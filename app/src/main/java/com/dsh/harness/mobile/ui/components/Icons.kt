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
    val AlternateEmail = Icons.Filled.AlternateEmail
    val FilterList = Icons.Filled.FilterList
    val FileDownload = Icons.Filled.FileDownload
    /** v22 修订版：侧栏「左滑会话可删除 / 归档」提示用 */
    val SwipeLeft = Icons.Filled.SwipeLeft
    val Settings = Icons.Filled.Settings
    val Lock = Icons.Filled.Lock
    val Info = Icons.Filled.Info
    val Warning = Icons.Filled.Warning
    val Error = Icons.Filled.Error
    val ErrorOutline = Icons.Outlined.ErrorOutline
    val Refresh = Icons.Filled.Refresh
    /** v22 修订版：S11 空工作区「上传文件」/「新建」用 */
    val Upload = Icons.Filled.Upload
    val NoteAdd = Icons.Filled.NoteAdd
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
    val AccountBalanceWallet = Icons.Filled.AccountBalanceWallet
    val OpenInNew = Icons.Filled.OpenInNew
    val ContentPaste = Icons.Filled.ContentPaste
    val DarkMode = Icons.Filled.DarkMode

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
    // —— v22 修订版：S3 工具行「权限档位」按钮 + 权限 Sheet 行图标 ——
    val Edit = Icons.Filled.Edit
    val Security = Icons.Filled.Security
    /** 附件（attach_file 图标，配合 S3 工具行与「附件」Sheet） */
    val AttachFile = Icons.Filled.AttachFile
    // —— v13 轨迹工具栏 / S26 检查器 ——
    val UnfoldMore = Icons.Filled.UnfoldMore
    val Compress = Icons.Filled.Compress
    val Timer = Icons.Filled.Timer
    val Restore = Icons.Filled.Restore
    val PlayCircle = Icons.Filled.PlayCircle
    val ContentCopy = Icons.Filled.ContentCopy

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

    // —— v20 新增屏（S30–S49 / S3 svc Sheet）——
    val Person = Icons.Filled.Person
    val TrendingUp = Icons.Filled.TrendingUp
    val FlashOn = Icons.Filled.FlashOn
    val Logout = Icons.Filled.Logout
    val RemoveCircleOutline = Icons.Outlined.RemoveCircleOutline
    val PauseCircleOutline = Icons.Outlined.PauseCircleOutline
    val Save = Icons.Filled.Save
    val History = Icons.Filled.History
    val NetworkCheck = Icons.Filled.NetworkCheck
    val Chat = Icons.Filled.Chat
    val AccountBalance = Icons.Filled.AccountBalance
    val ReceiptLong = Icons.Filled.ReceiptLong
    val FolderShared = Icons.Filled.FolderShared
    val Inventory2 = Icons.Filled.Inventory2
    val DataObject = Icons.Filled.DataObject
    val Minimize = Icons.Filled.Minimize
    val Source = Icons.Filled.Source
    val AdsClick = Icons.Filled.AdsClick
    val QueryBuilder = Icons.Filled.QueryBuilder
    val RadioButtonChecked = Icons.Filled.RadioButtonChecked
    val RadioButtonUnchecked = Icons.Outlined.RadioButtonUnchecked
    // v20 S14「默认模型与思考强度」行
    val AutoFixHigh = Icons.Filled.AutoFixHigh

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
        "error_outline" -> ErrorOutline; "warning" -> Warning;         "shield" -> Icons.Outlined.Shield
        "edit" -> Edit; "security" -> Security; "attach_file" -> AttachFile
        "create_new_folder" -> CreateNewFolder; "add_circle_outline" -> AddCircleOutline
        "settings" -> Settings; "search" -> Search; "filter_list" -> FilterList
        "filter_none" -> FilterNone; "subject" -> Subject; "file_download" -> FileDownload
        "more_horiz" -> MoreHoriz; "more_vert" -> MoreVert; "done" -> Done
        "auto_awesome" -> AutoAwesome; "psychology" -> Psychology
        "account_tree" -> AccountTree; "add" -> Add; "add_chart" -> AddChart
        "article" -> Article; "image" -> Image; "image_search" -> ImageSearch
        "slideshow" -> Slideshow; "table_chart" -> TableChart; "smartphone" -> Smartphone
        "public" -> Public; "palette" -> Palette; "help_outline" -> HelpOutline
        "schedule" -> Schedule;         "timeline" -> Timeline; "cached" -> Cached
        "repeat" -> Icons.Filled.Repeat; "call_split" -> CallSplit
        "unfold_more" -> UnfoldMore; "compress" -> Compress; "timer" -> Timer
        "restore" -> Restore; "play_circle" -> PlayCircle; "content_copy" -> ContentCopy
        "settings_input_component" -> SettingsInputComponent; "vpn_key" -> VpnKey
        "draft" -> Draft; "visibility" -> Visibility; "zoom_in" -> Icons.Filled.ZoomIn
        "account_balance_wallet" -> AccountBalanceWallet; "open_in_new" -> OpenInNew
        "content_paste" -> ContentPaste; "dark_mode" -> DarkMode; "vpn_key" -> VpnKey
        else -> null
    }
}