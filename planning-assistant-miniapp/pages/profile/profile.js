const { get, put } = require('../../utils/request');

const DEFAULT_AVATAR_KEY = 'cat-orange';
const AVATAR_OPTIONS = [
  { key: 'cat-orange', label: '橘猫', src: '/assets/images/avatars/cat-orange.png' },
  { key: 'cat-black', label: '黑猫', src: '/assets/images/avatars/cat-black.png' },
  { key: 'cat-calico', label: '三花猫', src: '/assets/images/avatars/cat-calico.png' },
  { key: 'cat-gray', label: '灰猫', src: '/assets/images/avatars/cat-gray.png' },
  { key: 'cat-white', label: '白猫', src: '/assets/images/avatars/cat-white.png' },
  { key: 'cat-ragdoll', label: '布偶', src: '/assets/images/avatars/cat-ragdoll.png' },
  { key: 'cat-tuxedo', label: '奶牛', src: '/assets/images/avatars/cat-tuxedo.png' },
  { key: 'cat-siamese', label: '暹罗', src: '/assets/images/avatars/cat-siamese.png' },
  { key: 'cat-tabby', label: '虎斑', src: '/assets/images/avatars/cat-tabby.png' },
];

function normalizeAvatarKey(avatarKey) {
  return AVATAR_OPTIONS.some((item) => item.key === avatarKey) ? avatarKey : DEFAULT_AVATAR_KEY;
}

function avatarSrc(avatarKey) {
  return AVATAR_OPTIONS.find((item) => item.key === normalizeAvatarKey(avatarKey)).src;
}

Page({
  data: {
    userInfo: { nickname: '', avatar: DEFAULT_AVATAR_KEY },
    avatarSrc: avatarSrc(DEFAULT_AVATAR_KEY),
    avatarOptions: AVATAR_OPTIONS,
    joinDate: '',
    editVisible: false,
    editingNickname: '',
    editingAvatar: DEFAULT_AVATAR_KEY,
    savingProfile: false,
    contactVisible: false,
  },

  onLoad() {
    if (!getApp().checkAuth()) return;
    this._loadProfile();
  },

  onShow() {
    this._closeOverlays();
    this._loadProfile();
  },

  onHide() {
    this._closeOverlays();
  },

  _loadProfile() {
    const cached = getApp().globalData.userInfo;
    if (cached) this._applyProfile(cached, false);

    get('/user/profile')
      .then((profile) => this._applyProfile(profile, true))
      .catch(() => {
        // 请求失败时保留已缓存的资料。
      });
  },

  _applyProfile(profile, saveToGlobal) {
    const normalizedProfile = Object.assign({}, profile, {
      avatar: normalizeAvatarKey(profile.avatar),
    });
    if (saveToGlobal) getApp().setUserInfo(normalizedProfile);
    this.setData({
      userInfo: normalizedProfile,
      avatarSrc: avatarSrc(normalizedProfile.avatar),
      joinDate: this._formatJoinDate(normalizedProfile.createdAt),
    });
  },

  goToStats() {
    this._closeOverlays();
    wx.switchTab({ url: '/pages/expense/stats/stats' });
  },

  goToBudget() {
    this._closeOverlays();
    wx.navigateTo({ url: '/pages/plan/edit/edit' });
  },

  openEditPanel() {
    this.setData({
      editVisible: true,
      editingNickname: this.data.userInfo.nickname || '',
      editingAvatar: normalizeAvatarKey(this.data.userInfo.avatar),
    });
  },

  closeEditPanel() {
    this._closeOverlays();
  },

  stopModalPropagation() {},

  onNicknameInput(e) {
    this.setData({ editingNickname: e.detail.value });
  },

  selectAvatar(e) {
    if (this.data.savingProfile) return;
    this.setData({ editingAvatar: normalizeAvatarKey(e.currentTarget.dataset.key) });
  },

  saveProfile() {
    const nickname = (this.data.editingNickname || '').trim();
    if (!nickname) {
      wx.showToast({ title: '请输入昵称', icon: 'none' });
      return;
    }
    this._saveProfile({ nickname, avatar: this.data.editingAvatar });
  },

  _saveProfile(payload) {
    if (this.data.savingProfile) return;
    this.setData({ savingProfile: true });
    put('/user/profile', payload)
      .then((profile) => {
        this._applyProfile(profile, true);
        this.setData({ editVisible: false });
        wx.showToast({ title: '保存成功', icon: 'success' });
      })
      .catch((err) => wx.showToast({ title: err.message || '保存失败', icon: 'none' }))
      .finally(() => this.setData({ savingProfile: false }));
  },

  openContact() {
    this.setData({ contactVisible: true });
  },

  closeContact() {
    this._closeOverlays();
  },

  _closeOverlays() {
    this.setData({ editVisible: false, contactVisible: false });
  },

  _formatJoinDate(createdAt) {
    if (!createdAt) return '';
    const date = new Date(createdAt);
    if (Number.isNaN(date.getTime())) return '';
    return `${date.getFullYear()} 年 ${date.getMonth() + 1} 月`;
  },
});
