export const capsFirstLetter = (text) =>{
    return String(text).charAt(0).toUpperCase() + String(text).slice(1);
}

export function filterObject(obj, predicate) {
    return Object.keys(obj)
      .filter(key => predicate(obj[key], key))
      .reduce((res, key) => {
        res[key] = obj[key];
        return res;
      }, {});
  }