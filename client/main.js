(function () {
	'use strict';

	const API_BASE = window.API_BASE || '/server/SkillBridge';

	let learnings = [];

	function get(path) {
		return fetch(API_BASE + path, {
			method: 'GET',
			headers: { 'Accept': 'application/json' },
			credentials: 'include'
		});
	}

	function post(path, body) {
		return fetch(API_BASE + path, {
			method: 'POST',
			headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
			credentials: 'include',
			body: JSON.stringify(body)
		});
	}

	function showToast(message, isError) {
		const el = document.getElementById('toast');
		if (!el) return;
		el.textContent = message;
		el.classList.remove('hidden', 'toast-error');
		if (isError) el.classList.add('toast-error');
		el.classList.remove('hidden');
		setTimeout(function () { el.classList.add('hidden'); }, 4000);
	}

	function hideView(id) {
		const view = document.getElementById(id);
		if (view) view.classList.add('hidden');
	}

	function showView(id) {
		const view = document.getElementById(id);
		if (view) view.classList.remove('hidden');
	}

	function escapeHtml(s) {
		if (s == null) return '';
		const div = document.createElement('div');
		div.textContent = s;
		return div.innerHTML;
	}

	function formatDate(createdTime) {
		if (!createdTime) return '—';
		try {
			const d = new Date(createdTime);
			if (isNaN(d.getTime())) return createdTime;
			const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
			return months[d.getMonth()] + ' ' + d.getDate();
		} catch (e) {
			return createdTime || '—';
		}
	}

	function computeStats() {
		const total = learnings.length;
		const applied = learnings.filter(function (l) { return l.appliedCount > 0; }).length;
		const pending = total - applied;
		const progress = total > 0 ? Math.round((applied / total) * 100) : 0;
		return { total, applied, pending, progress };
	}

	function renderDashboard() {
		const stats = computeStats();
		const totalEl = document.getElementById('stat-total');
		const appliedEl = document.getElementById('stat-applied');
		const pendingEl = document.getElementById('stat-pending');
		const progressEl = document.getElementById('stat-progress');
		if (totalEl) totalEl.textContent = stats.total;
		if (appliedEl) appliedEl.textContent = stats.applied;
		if (pendingEl) pendingEl.textContent = stats.pending;
		if (progressEl) progressEl.textContent = stats.progress + '%';

		const pendingList = learnings.filter(function (l) { return l.appliedCount === 0; });
		// Oldest first (by createdTime), then take max 3 for dashboard cards
		const pendingByOldest = pendingList.slice().sort(function (a, b) {
			const ta = a.createdTime || '';
			const tb = b.createdTime || '';
			return ta.localeCompare(tb);
		});
		const top3Pending = pendingByOldest.slice(0, 3);

		const focusListEl = document.getElementById('focus-list');
		const focusEmptyEl = document.getElementById('focus-empty');
		const focusBadgeEl = document.getElementById('focus-pending-badge');
		if (focusBadgeEl) focusBadgeEl.textContent = stats.pending + ' pending';

		if (focusListEl) {
			focusListEl.innerHTML = '';
			if (top3Pending.length === 0) {
				if (focusEmptyEl) focusEmptyEl.classList.remove('hidden');
			} else {
				if (focusEmptyEl) focusEmptyEl.classList.add('hidden');
				top3Pending.forEach(function (l) {
					const card = document.createElement('div');
					card.className = 'focus-item';
					card.setAttribute('data-learning-id', l.id);
					card.innerHTML = '<span class="focus-topic">' + escapeHtml(l.topic) + '</span>' +
						'<span class="badge badge-cat">' + escapeHtml(l.category) + '</span>' +
						'<span class="focus-date">Not started</span>' +
						'<button type="button" class="btn btn-small">Open →</button>';
					card.addEventListener('click', function (e) {
						openLearningDetail(l.id);
					});
					focusListEl.appendChild(card);
				});
			}
		}

		const needsListEl = document.getElementById('needs-list');
		const needsEmptyEl = document.getElementById('needs-empty');
		if (needsListEl) {
			needsListEl.innerHTML = '';
			if (top3Pending.length === 0) {
				if (needsEmptyEl) needsEmptyEl.classList.remove('hidden');
			} else {
				if (needsEmptyEl) needsEmptyEl.classList.add('hidden');
				top3Pending.forEach(function (l) {
					const card = document.createElement('div');
					card.className = 'needs-item';
					card.setAttribute('data-learning-id', l.id);
					card.innerHTML = '<span class="needs-topic">' + escapeHtml(l.topic) + '</span>' +
						'<span class="badge badge-cat">' + escapeHtml(l.category) + '</span>' +
						'<span class="needs-status">Not Started</span>' +
						'<button type="button" class="btn btn-icon">→</button>';
					card.addEventListener('click', function () { openLearningDetail(l.id); });
					needsListEl.appendChild(card);
				});
			}
		}
	}

	function renderListViewModel() {
		const stats = computeStats();
		const needs = learnings.filter(function (l) { return l.appliedCount === 0; });
		const applied = learnings.filter(function (l) { return l.appliedCount > 0; });

		const subtitleEl = document.getElementById('list-subtitle');
		const needsCountEl = document.getElementById('needs-count');
		const appliedCountEl = document.getElementById('applied-count');
		if (subtitleEl) subtitleEl.textContent = learnings.length + ' items tracked';
		if (needsCountEl) needsCountEl.textContent = needs.length;
		if (appliedCountEl) appliedCountEl.textContent = applied.length;

		function renderCardList(containerId, list, isClickable) {
			const container = document.getElementById(containerId);
			if (!container) return;
			container.innerHTML = '';
			list.forEach(function (l) {
				const card = document.createElement('div');
				card.className = 'learning-card';
				card.setAttribute('data-learning-id', l.id);
				card.innerHTML =
					'<span class="learning-card-topic">' + escapeHtml(l.topic) + '</span>' +
					'<span class="badge badge-cat">' + escapeHtml(l.category) + '</span>' +
					'<span class="badge badge-' + (l.status === 'APPLIED' ? 'applied' : 'pending') + '">' + escapeHtml(l.status) + '</span>' +
					'<span class="learning-card-date">' + formatDate(l.createdTime) + '</span>' +
					'<span class="learning-card-arrow">→</span>';
				if (isClickable !== false) card.addEventListener('click', function () { openLearningDetail(l.id); });
				container.appendChild(card);
			});
		}

		renderCardList('learnings-needs-tbody', needs);
		renderCardList('learnings-applied-tbody', applied);
	}

	function openLearningDetail(learningId) {
		document.getElementById('input-learning-id').value = learningId;
		hideView('dashboard-view');
		hideView('list-view');
		showView('detail-view');
		var headerActions = document.getElementById('header-actions');
		if (headerActions) headerActions.classList.add('hidden');
		loadLearningDetail(learningId);
	}

	function loadLearningDetail(learningId) {
		get('/api/learning/' + learningId)
			.then(function (res) {
				if (!res.ok) return res.json().then(function (d) { throw new Error(d.error || res.statusText); });
				return res.json();
			})
			.then(function (data) {
				if (!data) return;
				renderDetailLearning(data.learning);
				renderAppliedCards(data.appliedSkills || []);
			})
			.catch(function (err) {
				showToast(err.message || 'Failed to load learning.', true);
			});
	}

	function renderDetailLearning(learning) {
		const panel = document.getElementById('detail-learning-panel');
		if (!panel) return;
		panel.innerHTML =
			'<h2 class="detail-topic">' + escapeHtml(learning.topic) + '</h2>' +
			'<div class="detail-tags">' +
			'<span class="badge badge-cat">' + escapeHtml(learning.category) + '</span>' +
			'<span class="badge badge-' + (learning.status === 'APPLIED' ? 'applied' : 'pending') + '">' + escapeHtml(learning.status) + '</span>' +
			'<span class="detail-date">Started ' + formatDate(learning.createdTime) + '</span>' +
			'</div>' +
			'<p class="detail-source">Source: ' + escapeHtml(learning.source || '—') + '</p>';
	}

	function renderAppliedCards(appliedSkills) {
		const container = document.getElementById('applied-cards');
		const emptyEl = document.getElementById('applied-empty');
		if (!container) return;
		container.innerHTML = '';
		if (!appliedSkills || appliedSkills.length === 0) {
			if (emptyEl) emptyEl.classList.remove('hidden');
			return;
		}
		if (emptyEl) emptyEl.classList.add('hidden');
		appliedSkills.forEach(function (a) {
			const card = document.createElement('div');
			card.className = 'applied-card';
			card.innerHTML =
				'<div class="applied-card-tags">' +
				'<span class="badge badge-type">' + escapeHtml(a.type) + '</span>' +
				'</div>' +
				'<p class="applied-card-action">' + (a.applied_action ? escapeHtml(a.applied_action) : '') + '</p>' +
				'<p class="applied-card-notes">' + (a.notes ? escapeHtml(a.notes) : '—') + '</p>' +
				'<span class="applied-card-date">' + formatDate(a.createdTime) + '</span>';
			container.appendChild(card);
		});
	}

	function loadLearnings() {
		get('/api/learning')
			.then(function (res) {
				if (!res.ok) return res.json().then(function (d) { throw new Error(d.error || res.statusText); });
				return res.json();
			})
			.then(function (data) {
				learnings = Array.isArray(data) ? data : [];
				renderDashboard();
				renderListViewModel();
			})
			.catch(function (err) {
				showToast(err.message || 'Failed to load learnings.', true);
				learnings = [];
				renderDashboard();
				renderListViewModel();
			});
	}

	function showDashboard() {
		hideView('list-view');
		hideView('detail-view');
		showView('dashboard-view');
		var headerActions = document.getElementById('header-actions');
		if (headerActions) headerActions.classList.remove('hidden');
		loadLearnings();
	}

	function showListView() {
		hideView('dashboard-view');
		hideView('detail-view');
		showView('list-view');
		var headerActions = document.getElementById('header-actions');
		if (headerActions) headerActions.classList.add('hidden');
		loadLearnings();
	}

	function showDetailView() {
		hideView('dashboard-view');
		hideView('list-view');
		showView('detail-view');
	}

	function goBackToList() {
		hideView('detail-view');
		showView('list-view');
		loadLearnings();
	}

	function openModal(id) {
		document.getElementById(id).classList.remove('hidden');
		document.body.classList.add('modal-open');
	}

	function closeModal(id) {
		document.getElementById(id).classList.add('hidden');
		document.body.classList.remove('modal-open');
	}

	function handleAddLearningSubmit(e) {
		e.preventDefault();
		const topic = document.getElementById('input-topic').value.trim();
		const category = document.getElementById('input-category').value;
		const source = (document.getElementById('input-source').value || '').trim();
		if (!topic || !category) {
			showToast('Topic and category are required.', true);
			return;
		}

		post('/api/learning', { topic: topic, category: category, source: source || undefined })
			.then(function (res) {
				if (!res.ok) return res.json().then(function (d) { throw new Error(d.error || res.statusText); });
				return res.json();
			})
			.then(function () {
				document.getElementById('form-add-learning').reset();
				closeModal('modal-add-learning');
				showToast('Learning added.');
				loadLearnings();
			})
			.catch(function (err) {
				showToast(err.message || 'Failed to add learning.', true);
			});
	}

	function handleAddAppliedSubmit(e) {
		e.preventDefault();
		const learningId = document.getElementById('input-learning-id').value;
		const type = document.getElementById('input-type').value;
		const notes = (document.getElementById('input-notes').value || '').trim();
		const appliedAction = (document.getElementById('input-applied-action').value || '').trim();
		if (!learningId || !type) {
			showToast('Type is required.', true);
			return;
		}

		post('/api/learning/' + learningId + '/applied', {
			type: type,
			notes: notes || undefined,
			applied_action: appliedAction || undefined
		})
			.then(function (res) {
				if (!res.ok) return res.json().then(function (d) { throw new Error(d.error || res.statusText); });
				return res.json();
			})
			.then(function () {
				document.getElementById('input-type').value = '';
				document.getElementById('input-notes').value = '';
				document.getElementById('input-applied-action').value = '';
				closeModal('modal-add-applied');
				showToast('Applied skill added.');
				loadLearningDetail(learningId);
			})
			.catch(function (err) {
				showToast(err.message || 'Failed to add applied skill.', true);
			});
	}

	function bindEvents() {
		document.getElementById('btn-view-learning').addEventListener('click', function (e) {
			e.preventDefault();
			showListView();
		});
		document.getElementById('btn-add-learning').addEventListener('click', function () {
			openModal('modal-add-learning');
		});
		document.getElementById('btn-back-to-dashboard').addEventListener('click', showDashboard);
		document.getElementById('btn-back-to-list').addEventListener('click', goBackToList);
		document.getElementById('btn-add-applied-skill').addEventListener('click', function () {
			openModal('modal-add-applied');
		});

		document.getElementById('form-add-learning').addEventListener('submit', handleAddLearningSubmit);
		document.getElementById('form-add-applied').addEventListener('submit', handleAddAppliedSubmit);

		document.getElementById('modal-add-learning-close').addEventListener('click', function () { closeModal('modal-add-learning'); });
		document.getElementById('modal-add-learning-backdrop').addEventListener('click', function () { closeModal('modal-add-learning'); });
		document.getElementById('btn-cancel-add-learning').addEventListener('click', function () { closeModal('modal-add-learning'); });

		document.getElementById('modal-add-applied-close').addEventListener('click', function () { closeModal('modal-add-applied'); });
		document.getElementById('modal-add-applied-backdrop').addEventListener('click', function () { closeModal('modal-add-applied'); });
		document.getElementById('btn-cancel-add-applied').addEventListener('click', function () { closeModal('modal-add-applied'); });
	}

	function init() {
		bindEvents();
		loadLearnings();
	}

	if (document.readyState === 'loading') {
		document.addEventListener('DOMContentLoaded', init);
	} else {
		init();
	}
})();
