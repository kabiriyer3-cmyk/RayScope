package com.rayscope.app.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rayscope.app.BuildConfig
import com.rayscope.app.core.CoreManager
import com.rayscope.app.core.XrayConfig
import com.rayscope.app.data.Actions
import com.rayscope.app.data.Backup
import com.rayscope.app.data.Repo
import com.rayscope.app.data.UiPrefs
import com.rayscope.app.net.IpCheckModel
import com.rayscope.app.util.Fmt
import com.rayscope.app.util.toast
import com.rayscope.app.widget.SmallWidget
import com.rayscope.app.widget.WideWidget
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Ask the launcher to pin a widget; falls back to a hint when pinning is not supported. */
fun pinWidget(ctx: Context, cls: Class<*>) {
    var ok = false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        try {
            val m = AppWidgetManager.getInstance(ctx)
            if (m != null && m.isRequestPinAppWidgetSupported) {
                ok = m.requestPinAppWidget(ComponentName(ctx, cls), null, null)
            }
        } catch (e: Exception) {
            ok = false
        }
    }
    if (!ok) toast("Долгое нажатие на рабочем столе → Виджеты → RayScope")
}

// ------------------------------------------------------------------ look & feel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LookPrefs() {
    val c = ray
    val ui by Repo.ui.collectAsState()
    val ctx = LocalContext.current
    val dynamicOk = Build.VERSION.SDK_INT >= 31

    LookPreview()

    Group("Тема", modifier = Modifier.appear(1)) {
        Segmented(
            listOf("Система", "Светлая", "Тёмная"),
            selected = ui.theme,
            onSelect = { v -> Repo.setUi { it.copy(theme = v) } },
            modifier = Modifier.padding(12.dp)
        )
        Hairline(0.dp)
        PrefSwitch(
            null, c.hint, "AMOLED", "Чисто чёрный фон в тёмной теме, бережёт батарею на OLED",
            ui.amoled, divider = false
        ) { v -> Repo.setUi { it.copy(amoled = v) } }
    }

    Group(
        "Акцент",
        modifier = Modifier.appear(2),
        footer = "Один цвет на всё приложение: кнопка подключения, выбранный сервер, графики и виджеты."
    ) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            UiPrefs.ACCENT_NAMES.forEachIndexed { i, _ ->
                Swatch(accentFor(i, c.dark), ui.accent == i) { Repo.setUi { it.copy(accent = i) } }
            }
        }
        Hairline(0.dp)
        PrefRow(
            null, c.hint, "Цвет",
            value = if (ui.accent < 0) "Material You" else UiPrefs.ACCENT_NAMES.getOrElse(ui.accent) { "" },
            divider = dynamicOk
        )
        if (dynamicOk) {
            PrefSwitch(
                null, c.hint, "Material You", "Взять цвет из обоев (Android 12+)",
                ui.accent < 0, divider = false
            ) { v -> Repo.setUi { it.copy(accent = if (v) -1 else 0) } }
        }
    }

    Group("Форма и текст", modifier = Modifier.appear(3)) {
        PrefRow(null, c.hint, "Скругления", divider = false)
        Segmented(
            listOf("Острые", "Обычные", "Мягкие"),
            selected = ui.corners,
            onSelect = { v -> Repo.setUi { it.copy(corners = v) } },
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
        )
        Hairline(0.dp)
        PrefRow(null, c.hint, "Размер текста", divider = false)
        Segmented(
            listOf("Мелкий", "Обычный", "Крупный"),
            selected = ui.textScale,
            onSelect = { v -> Repo.setUi { it.copy(textScale = v) } },
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
        )
    }

    Group("Навигация", modifier = Modifier.appear(4)) {
        PrefChoice(
            RayIcons.Home, c.hint, "Открывать при запуске",
            options = listOf("Главная", "Серверы", "Проверка"),
            selected = ui.startTab
        ) { v -> Repo.setUi { it.copy(startTab = v) } }
        PrefSwitch(
            RayIcons.Apps, c.hint, "Подписи вкладок", "Текст под иконками в нижней панели",
            ui.tabLabels
        ) { v -> Repo.setUi { it.copy(tabLabels = v) } }
        PrefSwitch(
            RayIcons.Vibrate, c.hint, "Вибрация", "Короткий отклик, когда туннель поднялся или упал",
            ui.haptics, divider = false
        ) { v -> Repo.setUi { it.copy(haptics = v) } }
    }

    Group("Списки и движение", modifier = Modifier.appear(5)) {
        PrefSwitch(
            RayIcons.Layers, c.hint, "Компактный список", "Больше серверов на экране",
            ui.compact
        ) { v -> Repo.setUi { it.copy(compact = v) } }
        PrefSwitch(
            RayIcons.Globe, c.hint, "Флаги стран", "Иначе нейтральная иконка",
            ui.flags
        ) { v -> Repo.setUi { it.copy(flags = v) } }
        PrefSwitch(
            RayIcons.Play, c.hint, "Анимации", "Плавные переходы между экранами",
            ui.motion, divider = false
        ) { v -> Repo.setUi { it.copy(motion = v) } }
    }

    Group(
        "Виджеты на рабочий стол",
        modifier = Modifier.appear(6),
        footer = "Виджеты тёмные и берут цвет акцента. Кнопка на виджете подключает и отключает VPN."
    ) {
        PrefRow(
            RayIcons.Power, c.hint, "Кнопка 2×2", subtitle = "Подключение одним нажатием",
            chevron = true, onClick = { pinWidget(ctx, SmallWidget::class.java) }
        )
        PrefRow(
            RayIcons.Signal, c.hint, "Панель 4×1", subtitle = "Сервер, статус, пинг и скорость",
            chevron = true, divider = false, onClick = { pinWidget(ctx, WideWidget::class.java) }
        )
    }

    PillButton(
        "Сбросить оформление",
        onClick = {
            Repo.setUi { UiPrefs(dev = it.dev) }
            toast("Оформление сброшено")
        },
        icon = RayIcons.Refresh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    val c = ray
    Box(
        Modifier.size(34.dp).bounceClick(onClick = onClick).clip(CircleShape)
            .border(1.5.dp, if (selected) c.text else Color.Transparent, CircleShape)
            .padding(4.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                RayIcons.Check, contentDescription = null,
                tint = if (color.luminance() > 0.45f) InkOn else Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/** Live sample of the main UI pieces with the current look settings. */
@Composable
private fun LookPreview() {
    val c = ray
    Column(Modifier.appear(0).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).card(c).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Good))
            Spacer(Modifier.width(10.dp))
            Text("Подключено", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("00:12:48", color = c.hint, fontSize = 13.sp, style = TNum)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().clip(c.shape(16.dp)).background(c.raised).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FlagBox(Fmt.flag("NL"), 30.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Amsterdam #1", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("vless · tcp · reality", color = c.hint, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            LatencyText(42L, 14.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Все", selected = true, small = true) {}
            Chip("Рабочие", selected = false, small = true) {}
            Chip("Избранное", selected = false, small = true) {}
        }
        Spacer(Modifier.height(10.dp))
        PillButton("Подключить", onClick = {}, primary = true, icon = RayIcons.Power, modifier = Modifier.fillMaxWidth())
    }
}

// ------------------------------------------------------------------ backup

@Composable
fun BackupPrefs() {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var confirmReset by remember { mutableStateOf(false) }
    val c = ray

    Group(
        null,
        modifier = Modifier.appear(0),
        footer = "В копию входят все настройки, адреса подписок и серверы, добавленные ссылками. В них есть ключи доступа, поэтому храните копию как пароль."
    ) {
        PrefRow(
            RayIcons.Upload, c.hint, "Скопировать резервную копию", "JSON в буфер обмена, можно переслать себе",
            onClick = {
                clipboard.setText(AnnotatedString(Backup.export(ctx)))
                toast("Резервная копия в буфере")
            }
        )
        PrefRow(
            RayIcons.Download, c.hint, "Восстановить из буфера", "Настройки заменятся, подписки и ссылки добавятся",
            divider = false,
            onClick = {
                val text = clipboard.getText()?.text.orEmpty()
                scope.launch {
                    try {
                        toast("Восстановлено: " + Backup.import(ctx, text))
                    } catch (e: IllegalArgumentException) {
                        toast(e.message ?: "Не получилось восстановить")
                    }
                }
            }
        )
    }

    Group("Сброс", modifier = Modifier.appear(1), footer = "Серверы и подписки останутся, вернутся только значения по умолчанию.") {
        PrefRow(RayIcons.Trash, Bad, "Сбросить все настройки", divider = false, onClick = { confirmReset = true })
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = c.surface,
            shape = c.shape(24.dp),
            title = { Text("Сбросить все настройки?", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
            text = { Text("Подключение, пинг, DNS, маршруты и оформление вернутся к значениям по умолчанию.", color = c.hint) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    Backup.reset(ctx)
                    toast("Настройки сброшены")
                }) { Text("Сбросить", color = Bad) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Отмена", color = c.hint) } }
        )
    }
}

// ------------------------------------------------------------------ developer mode

@Composable
fun DevPrefs(onOpen: (Route) -> Unit) {
    val c = ray
    val ui by Repo.ui.collectAsState()
    val ip by IpCheckModel.state.collectAsState()
    val servers by Repo.servers.collectAsState()
    val subs by Repo.subs.collectAsState()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val st = Repo.settings

    Group(
        null,
        modifier = Modifier.appear(0),
        footer = "Показывает технические детали по всему приложению: TCP-пинг, сырые ссылки, JSON, ASN, маршрут, тонкие настройки ядра. Включается 7 нажатиями на «Версия» в настройках."
    ) {
        PrefSwitch(RayIcons.Code, c.hint, "Режим разработчика", null, ui.dev, divider = false) { v ->
            Repo.setUi { it.copy(dev = v) }
        }
    }

    Group("Инструменты", modifier = Modifier.appear(1)) {
        PrefRow(RayIcons.Logs, c.hint, "Журнал событий", chevron = true, onClick = { onOpen(Route.Logs) })
        PrefRow(
            RayIcons.Hash, c.hint, "Локальный прокси",
            subtitle = "SOCKS ${st.socksPort} · HTTP ${st.httpPort}" + (if (st.allowLan) " · LAN" else ""),
            chevron = true, onClick = { onOpen(Route.Pref(SetPage.PROXY)) }
        )
        PrefRow(
            RayIcons.Code, c.hint, "JSON текущего сервера", subtitle = "Полный конфиг Xray в буфер",
            onClick = {
                val s = Repo.selected()
                if (s == null) {
                    toast("Нет выбранного сервера")
                } else {
                    try {
                        val json = JSONObject(XrayConfig.build(s, Repo.settings, tun = false)).toString(2)
                        clipboard.setText(AnnotatedString(json))
                        toast("JSON-конфиг скопирован")
                    } catch (e: Exception) {
                        toast("Ошибка: ${e.message}")
                    }
                }
            }
        )
        PrefRow(
            RayIcons.Share, c.hint, "Скопировать все ссылки", value = "${servers.size}",
            onClick = {
                clipboard.setText(AnnotatedString(Actions.exportAll()))
                toast("Ссылки скопированы")
            }
        )
        PrefRow(
            RayIcons.Signal, c.hint, "TCP-пинг всех", subtitle = "Только рукопожатие с портом, без ядра",
            onClick = {
                scope.launch { Actions.tcpAll() }
                toast("TCP-пинг запущен")
            }
        )
        PrefRow(
            RayIcons.Clock, c.hint, "Сбросить результаты проверок", subtitle = "Пинг, стабильность и ошибки всех серверов",
            divider = false,
            onClick = {
                Repo.resetTests()
                toast("Результаты сброшены")
            }
        )
    }

    Group(
        "История проверок IP",
        modifier = Modifier.appear(2),
        trailing = {
            if (ip.history.isNotEmpty()) {
                MiniAction(RayIcons.Trash, "Очистить", Bad) {
                    IpCheckModel.state.update { it.copy(history = emptyList()) }
                }
            }
        }
    ) {
        if (ip.history.isEmpty()) {
            Text(
                "Пока пусто. Запустите проверку на вкладке «Проверка».",
                color = c.hint, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )
        } else {
            Column(Modifier.padding(vertical = 8.dp)) {
                ip.history.forEach {
                    Text(
                        it, color = c.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }

    Group("Сборка и устройство", modifier = Modifier.appear(3)) {
        KV("Версия", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", mono = true)
        KV("Ядро Xray", CoreManager.version(), mono = true)
        KV("Android", "${Build.VERSION.RELEASE} · SDK ${Build.VERSION.SDK_INT}")
        KV("Устройство", "${Build.MANUFACTURER} ${Build.MODEL}")
        KV("Серверы / подписки", "${servers.size} / ${subs.size}", mono = true)
        KV("Режим пинга", Actions.modeLabel())
        KV("Прокси", "socks ${st.socksPort} · http ${st.httpPort}", mono = true)
    }
}
