/**
 * Unified HTTP request helper.
 *
 * API 根地址统一由 utils/config.js 提供。
 */

const { API_BASE_URL } = require('./config');

/**
 * @param {object} options
 * @param {'GET'|'POST'|'PUT'|'DELETE'} options.method
 * @param {string}  options.url     Path relative to /api, e.g. '/expense'
 * @param {object}  [options.data]  Query params (GET) or request body (POST/PUT)
 * @returns {Promise<object>}       Resolves with the `data` field of a 200 response
 *                                  Rejects with { code, message } on API errors
 */
function request({ method = 'GET', url, data = {} }) {
  return _realRequest(method, url, data);
}

function _realRequest(method, url, data) {
  return new Promise((resolve, reject) => {
    const app = getApp();
    const token = (app && app.globalData && app.globalData.token)
      || wx.getStorageSync('token')
      || '';

    const header = {
      'Content-Type': 'application/json',
    };
    if (token) {
      header['Authorization'] = `Bearer ${token}`;
    }

    const isGet = method === 'GET';

    wx.request({
      url: API_BASE_URL + url,
      method,
      data: isGet ? data : JSON.stringify(data),
      header,
      success(res) {
        const body = res.data;
        if (res.statusCode === 401 || (body && body.code === 401)) {
          // Token expired — redirect to login
          wx.removeStorageSync('token');
          wx.reLaunch({ url: '/pages/login/login' });
          reject({ code: 401, message: '登录已过期，请重新登录' });
          return;
        }
        if (res.statusCode >= 500) {
          reject({ code: res.statusCode, message: '服务器异常，请稍后重试' });
          return;
        }
        if (!body || typeof body.code === 'undefined') {
          reject({ code: -1, message: '响应格式错误' });
          return;
        }
        if (body.code !== 200) {
          if (body.code >= 500) {
            reject({ code: body.code, message: '服务器异常，请稍后重试' });
            return;
          }
          reject({ code: body.code, message: body.message || '请求失败' });
          return;
        }
        resolve(body.data);
      },
      fail(err) {
        reject({ code: -1, message: '网络异常，请稍后重试' });
      },
    });
  });
}

// ─── Convenience methods ──────────────────────────────────────────────────────

const get  = (url, data)  => request({ method: 'GET',    url, data });
const post = (url, data)  => request({ method: 'POST',   url, data });
const put  = (url, data)  => request({ method: 'PUT',    url, data });
const del  = (url, data)  => request({ method: 'DELETE', url, data });

module.exports = { request, get, post, put, del };
