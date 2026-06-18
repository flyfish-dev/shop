import { del, get, post, put } from '@/network/request.js';

export const PortalUsers = {
  current: async (options = {}) => {
    return get(`/portal/users/current`, {
      credential: true,
      ...options
    });
  },
  updateCurrent: async body => {
    return put('/portal/users/current', {
      credential: true,
      body
    });
  },
  uploadAvatar: async body => {
    return post('/portal/users/current/avatar', {
      credential: true,
      body
    });
  },
  logout: async (options = {}) => {
    return post('/portal/users/logout', {
      credential: true,
      ...options
    });
  },
  prepareBinding: async (provider, redirect = '/account/profile') => {
    return post(`/oauth/bind/${provider}`, {
      credential: true,
      params: {
        redirect
      }
    });
  },
  unbind: async type => {
    return del(`/portal/users/authorizations/${type}`, {
      credential: true
    });
  }
};

export const PortalOauth = {
  providers: async () => {
    return get('/oauth/providers');
  }
};

export const PortalFiles = {
  upload: async body => {
    return post('/portal/files', {
      credential: true,
      body
    });
  }
};
