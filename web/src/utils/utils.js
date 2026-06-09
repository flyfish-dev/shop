import isEqual from 'lodash/isEqual'
import _isEmpty from 'lodash/isEmpty'

export function setImmediate(cb) {
  return setTimeout(cb, 0);
}

export function last(array) {
  if (array.length) {
    return array[array.length - 1];
  }
  return null;
}

export function splitPath(uri, routes) {
  const path = uri.includes('?') ? uri.substring(0, uri.indexOf('?')) : uri;
  const paths = path === '/' ? ['/'] : path.split('/').map(code => `/${code}`);
  return skipIndexPaths(routes, paths);
}

// 跳过根路径，如果第一路径匹配
function skipIndexPaths(routes, paths) {
  const [ , first ] = paths;
  if (routes[first]) {
    paths.shift();
  }
  return paths;
}

export function distinctBy(array, key) {
  const keys = [];
  return array.reduce((result, item) => {
    const picked = item[key];
    if (keys.includes(picked)) {
      return result;
    }
    keys.push(picked);
    result.push(item);
    return result;
  }, []);
}

export function isEmpty(obj) {
  return _isEmpty(obj);
}

/**
 * 深度filter，包括儿子
 * @param collection 集合
 * @param predicate 判定
 */
export function filterDeep(collection, predicate) {
  return collection.filter(predicate)
    .map(item => {
      if (item.children && item.children.length) {
        return {
          ...item,
          children: filterDeep(item.children, predicate),
        };
      }
      return item;
    });
}

/**
 * 内部缓存，提供更少的访问次数，避免同值反复请求
 */
export function memoizeOne(func, combiner) {
  const store = {
    saved: '',
    cache: {},
  };
  return (...args) => {
    const comparing = combiner ? combiner(...args) : args;
    if (isEqual(comparing, store.cache)) {
      return store.saved;
    }
    store.cache = comparing;
    const result = func(...args);
    store.saved = result;
    return result;
  };
}

var chsReg = /[\u4E00-\u9FA5]/;
export function isChs(charCode) {
  return charCode >= 0x4e00 && charCode <= 0x9fa5;
}

export function charCodes(text) {
  const result = [];
  for (let i = 0; i < text.length; i++) {
    result.push(text.charCodeAt(i));
  }
  return result;
}

export const computeWidth = text => {
  if (!text.length) return 0;
  return charCodes(text).map(c => isChs(c) ? 18 : 10).reduce((a, b) => a + b);
}
