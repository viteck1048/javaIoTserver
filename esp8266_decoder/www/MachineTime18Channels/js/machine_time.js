'use strict';

// ---- State ----
let currentDeviceId = null;
let availableEpochs = new Set();
let channelNames = [];       // 18 strings, index 0 = ch1 ... index 17 = ch18
let selectedDayEpoch = null;
let selectedDayData = null;
let activeDeviceEl = null;
let activeDayEl = null;
let deviceTodayEpoch = null;  // "today" according to the device's gadget_time

// ---- Helpers ----

function formatSeconds(sec) {
	const h = Math.floor(sec / 3600);
	const m = Math.floor((sec % 3600) / 60);
	const s = sec % 60;
	return `${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}`;
}

function epochToDateStr(epoch) {
	const d = new Date(epoch * 86400 * 1000);
	return `${String(d.getUTCDate()).padStart(2,'0')}.${String(d.getUTCMonth()+1).padStart(2,'0')}.${d.getUTCFullYear()}`;
}

function todayEpoch() {
	return Math.floor(Date.now() / 86400000);
}

// Day epoch for the device's local "today", parsed from gadget_time,
// e.g. "Sat, 20 Jun 2026 01:19:05". The wall-clock date is taken as-is
// (no timezone conversion) so it matches the device's local day.
const MONTH_ABBR = { Jan:0, Feb:1, Mar:2, Apr:3, May:4, Jun:5,
                     Jul:6, Aug:7, Sep:8, Oct:9, Nov:10, Dec:11 };
function gadgetEpoch(gadgetTimeStr) {
	const m = /(\d{1,2})\s+(\w{3})\s+(\d{4})/.exec(gadgetTimeStr || '');
	if (!m || !(m[2] in MONTH_ABBR)) return todayEpoch();  // fallback to UTC
	return Math.floor(Date.UTC(+m[3], MONTH_ABBR[m[2]], +m[1]) / 86400000);
}

// Returns the device's local today epoch, falling back to UTC when unknown.
function currentEpoch() {
	return deviceTodayEpoch !== null ? deviceTodayEpoch : todayEpoch();
}

function escapeAttr(s) {
	return s.replace(/&/g,'&amp;').replace(/"/g,'&quot;');
}

// ---- Sidebar ----

function initSidebar() {
	const sidebar = document.getElementById('sidebar');
	if (!sidebar) return;

	if (window.innerWidth <= 768)
		sidebar.classList.add('active');

	document.querySelector('.main-content').addEventListener('click', () => {
		if (window.innerWidth <= 768)
			sidebar.classList.remove('active');
	});
	// свайпи handled by transltAndSwop.js
}

function closeSidebarOnMobile() {
	if (window.innerWidth <= 768)
		document.getElementById('sidebar').classList.remove('active');
}

// ---- Device list ----

async function loadDevices() {
	const list = document.getElementById('device-list');
	try {
		const resp = await fetch('/MachineTime18Channels/?ids=true');
		if (!resp.ok) throw new Error(resp.status);
		const data = await resp.json();

		if (!data.ids || data.ids.length === 0) {
			list.innerHTML = `<li><span class="loading-message">${t('no_devices')}</span></li>`;
			return;
		}

		list.innerHTML = '';
		for (const id of data.ids) {
			const li = document.createElement('li');
			const a = document.createElement('a');
			a.href = '#';
			a.className = 'link-item';
			a.textContent = id;
			a.addEventListener('click', (e) => {
				e.preventDefault();
				if (activeDeviceEl) activeDeviceEl.classList.remove('active');
				activeDeviceEl = a;
				a.classList.add('active');
				closeSidebarOnMobile();
				selectDevice(id);
			});
			li.appendChild(a);
			list.appendChild(li);
		}
	} catch (err) {
		list.innerHTML = `<li><span class="loading-message" style="color:red">${t('error_prefix')}${err}</span></li>`;
	}
}

// ---- Select device → load calendar ----

async function selectDevice(id) {
	currentDeviceId = id;
	selectedDayEpoch = null;
	selectedDayData = null;
	if (activeDayEl) { activeDayEl.classList.remove('selected'); activeDayEl = null; }

	const calEl   = document.getElementById('mt-calendars');
	const detailEl = document.getElementById('mt-detail');
	calEl.innerHTML   = `<div class="mt-placeholder">${t('loading')}</div>`;
	detailEl.innerHTML = `<div class="mt-placeholder">${t('select_day')}</div>`;

	try {
		const [monthResp, namesResp] = await Promise.all([
			fetch(`/MachineTime18Channels/?id=${encodeURIComponent(id)}&day=0`),
			fetch(`/MachineTime18Channels/?id=${encodeURIComponent(id)}&name=true`)
		]);
		if (!monthResp.ok) throw new Error(monthResp.status);
		if (!namesResp.ok) throw new Error(namesResp.status);

		const monthData = await monthResp.json();
		const namesData = await namesResp.json();

		channelNames = namesData.names;
		availableEpochs = new Set(monthData.days.map(d => d.epoch));
		deviceTodayEpoch = gadgetEpoch(monthData.gadget_time);

		renderCalendars();
	} catch (err) {
		calEl.innerHTML = `<div class="mt-placeholder" style="color:red">${t('error_prefix')}${err}</div>`;
	}
}

// ---- Calendar ----

function buildCalendarHTML(year, month) {
	const daysInMonth = new Date(Date.UTC(year, month, 0)).getUTCDate();
	const firstDow = (new Date(Date.UTC(year, month - 1, 1)).getUTCDay() + 6) % 7; // Mon=0
	const te = currentEpoch();

	let h = `<div class="cal-widget">`;
	h += `<div class="cal-header">${t('month_names')[month - 1]} ${year}<span class="cal-download" data-year="${year}" data-month="${month}" title="${t('download_csv')}">⬇</span></div>`;
	h += `<div class="cal-grid">`;

	for (const d of t('day_abbr'))
		h += `<div class="cal-day-header">${d}</div>`;

	for (let i = 0; i < firstDow; i++)
		h += `<div class="cal-day"></div>`;

	for (let day = 1; day <= daysInMonth; day++) {
		const epoch = Math.floor(Date.UTC(year, month - 1, day) / 86400000);
		const todayCls   = epoch === te          ? ' today'    : '';
		const selCls     = epoch === selectedDayEpoch ? ' selected' : '';
		if (availableEpochs.has(epoch)) {
			h += `<div class="cal-day has-data${todayCls}${selCls}" data-epoch="${epoch}">${day}</div>`;
		} else {
			h += `<div class="cal-day no-data${todayCls}">${day}</div>`;
		}
	}

	h += `</div></div>`;
	return h;
}

function renderCalendars() {
	const now = new Date(currentEpoch() * 86400000);
	const cy = now.getUTCFullYear();
	const cm = now.getUTCMonth() + 1;

	let py = cy, pm = cm - 1;
	if (pm === 0) { pm = 12; py--; }

	const calEl = document.getElementById('mt-calendars');
	calEl.innerHTML = buildCalendarHTML(py, pm) + buildCalendarHTML(cy, cm)
		+ `<a href="#" class="edit-channels-link">${t('edit_channels')}</a>`;

	calEl.querySelectorAll('.cal-day.has-data').forEach(el => {
		el.addEventListener('click', () => {
			if (activeDayEl) activeDayEl.classList.remove('selected');
			activeDayEl = el;
			el.classList.add('selected');
			selectDay(parseInt(el.dataset.epoch));
		});
	});

	calEl.querySelectorAll('.cal-download').forEach(btn => {
		btn.addEventListener('click', (e) => {
			e.stopPropagation();
			downloadMonthCsv(parseInt(btn.dataset.year), parseInt(btn.dataset.month), btn);
		});
	});

	calEl.querySelector('.edit-channels-link').addEventListener('click', (e) => {
		e.preventDefault();
		if (activeDayEl) { activeDayEl.classList.remove('selected'); activeDayEl = null; }
		renderEditChannels();
	});
}

// ---- Select day → channel list ----

async function selectDay(epoch) {
	selectedDayEpoch = epoch;
	const detailEl = document.getElementById('mt-detail');
	detailEl.innerHTML = `<div class="mt-placeholder">${t('loading')}</div>`;

	try {
		const resp = await fetch(
			`/MachineTime18Channels/?id=${encodeURIComponent(currentDeviceId)}&day=${epoch}`
		);
		if (!resp.ok) throw new Error(resp.status);
		selectedDayData = await resp.json();
		renderChannelList(epoch);
	} catch (err) {
		detailEl.innerHTML = `<div class="mt-placeholder" style="color:red">${t('error_prefix')}${err}</div>`;
	}
}

function renderChannelList(epoch) {
	const detailEl = document.getElementById('mt-detail');
	const channels = selectedDayData.channels;

	const rows = [{ ch: 0, name: t('control_clock'), time: channels[0].time_seconds, working: channels[0].working_now }];
	for (let i = 1; i <= 18; i++) {
		const name = channelNames[i - 1];
		if (name && name.length > 0)
			rows.push({ ch: i, name, time: channels[i].time_seconds, working: channels[i].working_now });
	}

	let h = `<p class="day-title">${epochToDateStr(epoch)}</p>`;

	h += '<ul class="channel-ul">';
	for (const { ch, name, time, working } of rows) {
		const badge = working ? `<span class="working-badge">${t('working')}</span>` : '';
		h += `<li class="channel-item">
			<a href="#" class="channel-link" data-epoch="${epoch}" data-channel="${ch}">${name}</a>
			${badge}
			<span class="channel-time">${formatSeconds(time)}</span>
		</li>`;
	}
	h += '</ul>';
	detailEl.innerHTML = h;

	detailEl.querySelectorAll('.channel-link').forEach(el => {
		el.addEventListener('click', (e) => {
			e.preventDefault();
			selectChannel(
				parseInt(el.dataset.epoch),
				parseInt(el.dataset.channel),
				el.textContent.trim()
			);
		});
	});
}

// ---- Select channel → events list ----

async function selectChannel(epoch, channel, name) {
	const detailEl = document.getElementById('mt-detail');
	detailEl.innerHTML = `<div class="mt-placeholder">${t('loading')}</div>`;

	try {
		const resp = await fetch(
			`/MachineTime18Channels/?id=${encodeURIComponent(currentDeviceId)}&day=${epoch}&channel=${channel}`
		);
		if (!resp.ok) throw new Error(resp.status);
		const data = await resp.json();
		if (data.error) throw new Error(data.error);
		renderEventsList(epoch, channel, name, data);
	} catch (err) {
		detailEl.innerHTML = `
			<button class="back-btn" id="back-to-day">${t('back')}</button>
			<div class="mt-placeholder" style="color:red">${t('error_prefix')}${err}</div>`;
		document.getElementById('back-to-day').addEventListener('click', () => renderChannelList(epoch));
	}
}

function renderEventsList(epoch, channel, name, data) {
	const detailEl = document.getElementById('mt-detail');

	const events = [];
	for (const s of (data.starts || [])) events.push({ time: s, type: 'start' });
	for (const s of (data.stops  || [])) events.push({ time: s, type: 'stop'  });
	events.sort((a, b) => a.time - b.time);

	let h = `<button class="back-btn" id="back-to-day">${t('back')}</button>`;
	h += `<p class="events-title">${t('channel_label')} ${channel}: ${name} — ${epochToDateStr(epoch)}</p>`;

	if (events.length === 0) {
		h += `<div class="events-empty">${t('no_events')}</div>`;
	} else {
		h += '<div class="events-list">';
		for (const ev of events) {
			const icon = ev.type === 'start' ? '▶' : '■';
			h += `<div class="event-row ${ev.type}">
				<span class="event-icon">${icon}</span>
				<span>${formatSeconds(ev.time)}</span>
			</div>`;
		}
		h += '</div>';
	}

	detailEl.innerHTML = h;
	document.getElementById('back-to-day').addEventListener('click', () => renderChannelList(epoch));
}

// ---- CSV download ----

function csvEscape(val) {
	if (/[;"\n]/.test(val))
		return '"' + val.replace(/"/g, '""') + '"';
	return val;
}

async function downloadMonthCsv(year, month, btn) {
	if (btn.classList.contains('loading')) return;
	btn.classList.add('loading');
	btn.textContent = '…';

	try {
		// all days of the month up to today (future days of current month excluded)
		const daysInMonth = new Date(Date.UTC(year, month, 0)).getUTCDate();
		const te = currentEpoch();
		const allEpochs = [];
		for (let day = 1; day <= daysInMonth; day++) {
			const ep = Math.floor(Date.UTC(year, month - 1, day) / 86400000);
			if (ep > te) break;
			allEpochs.push(ep);
		}
		if (allEpochs.length === 0) return;

		// columns: ch0 + named channels 1-18
		const cols = [{ ch: 0, name: 'control clock' }];
		for (let i = 1; i <= 18; i++) {
			if (channelNames[i - 1] && channelNames[i - 1].length > 0)
				cols.push({ ch: i, name: channelNames[i - 1] });
		}

		// fetch all days in parallel (days without data return 404 → null)
		const results = await Promise.all(
			allEpochs.map(ep =>
				fetch(`/MachineTime18Channels/?id=${encodeURIComponent(currentDeviceId)}&day=${ep}`)
					.then(r => r.ok ? r.json() : null)
					.then(data => ({ ep, data }))
					.catch(() => ({ ep, data: null }))
			)
		);

		const header = ['Date', ...cols.map(c => csvEscape(c.name))].join(';');
		const rows = results.map(({ ep, data }) => {
			const vals = cols.map(c =>
				data ? formatSeconds(data.channels[c.ch].time_seconds) : '00:00:00'
			);
			return [epochToDateStr(ep), ...vals].join(';');
		});

		const csv = '﻿' + [header, ...rows].join('\r\n');
		const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
		const url = URL.createObjectURL(blob);
		const a = document.createElement('a');
		a.href = url;
		a.download = `machinetime_${currentDeviceId}_${year}-${String(month).padStart(2,'0')}.csv`;
		a.click();
		URL.revokeObjectURL(url);
	} finally {
		btn.textContent = '⬇';
		btn.classList.remove('loading');
	}
}

// ---- Edit channel names ----

function renderEditChannels() {
	const detailEl = document.getElementById('mt-detail');

	let h = `<p class="day-title">${t('edit_channels_title')}</p>`;
	h += '<ul class="edit-ul">';
	for (let ch = 1; ch <= 18; ch++) {
		const name = escapeAttr(channelNames[ch - 1] || '');
		h += `<li class="edit-item">
			<span class="edit-label">${t('channel_label')} ${ch}</span>
			<input type="text" class="edit-input" data-channel="${ch}" value="${name}">
			<button class="btn-save-ch" data-channel="${ch}">${t('save')}</button>
			<button class="btn-del-ch" data-channel="${ch}">${t('delete_name')}</button>
		</li>`;
	}
	h += '</ul>';
	detailEl.innerHTML = h;

	detailEl.querySelectorAll('.btn-save-ch').forEach(btn => {
		btn.addEventListener('click', async () => {
			const ch = parseInt(btn.dataset.channel);
			const input = detailEl.querySelector(`.edit-input[data-channel="${ch}"]`);
			const name = input.value.trim();
			try {
				const resp = await fetch('/MachineTime18Channels/', {
					method: 'PUT',
					headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
					body: new URLSearchParams({ id: currentDeviceId, channel: ch, name })
				});
				if (!resp.ok) throw new Error(resp.status);
				channelNames[ch - 1] = name;
				const orig = btn.textContent;
				btn.textContent = '✓';
				setTimeout(() => btn.textContent = orig, 1200);
			} catch (err) {
				btn.style.background = '#e74c3c';
				setTimeout(() => btn.style.background = '', 1200);
			}
		});
	});

	detailEl.querySelectorAll('.btn-del-ch').forEach(btn => {
		btn.addEventListener('click', async () => {
			const ch = parseInt(btn.dataset.channel);
			try {
				const resp = await fetch('/MachineTime18Channels/', {
					method: 'DELETE',
					headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
					body: new URLSearchParams({ id: currentDeviceId, channel: ch })
				});
				if (!resp.ok) throw new Error(resp.status);
				channelNames[ch - 1] = '';
				detailEl.querySelector(`.edit-input[data-channel="${ch}"]`).value = '';
				const orig = btn.textContent;
				btn.textContent = '✓';
				setTimeout(() => btn.textContent = orig, 1200);
			} catch (err) {
				btn.style.background = '#e67e22';
				setTimeout(() => btn.style.background = '', 1200);
			}
		});
	});
}

// ---- Init ----

window.onload = function () {
	initSidebar();
	loadDevices();
};
