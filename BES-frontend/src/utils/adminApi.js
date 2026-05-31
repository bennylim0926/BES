const domain = ""

export const addGenre = async(genreName)=>{
    try{
        return await fetch(`${domain}/api/v1/admin/genre`,{
            method: 'POST',
            credentials: 'include',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                name: genreName,
            })
        })
    }catch(_err){
        // network error — caller handles undefined return
    }
}

export const addJudge = async(judgeName)=>{
    try{
        return await fetch(`${domain}/api/v1/admin/judge`,{
            method: 'POST',
            credentials: 'include',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                judgeName: judgeName,
            })
        })
    }catch(_err){
        // network error — caller handles undefined return
    }
}

export const deleteGenre = async(id)=>{
    try{
        return await fetch(`${domain}/api/v1/admin/genre`,{
            method: 'DELETE',
            credentials: 'include',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                id: id,
            })
        })
    }catch(_err){
        // network error — caller handles undefined return
    }
}

export const deleteJudge = async(id)=>{
    try{
        return await fetch(`${domain}/api/v1/admin/judge`,{
            method: 'DELETE',
            credentials: 'include',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                id: id,
            })
        })
    }catch(_err){
        // network error — caller handles undefined return
    }
}

export const updateGenre = async(id, newName, aliases)=>{
    try{
        await fetch(`${domain}/api/v1/admin/update-genre`,{
            method: 'POST',
            credentials: 'include',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                id: id,
                newName: newName,
                aliases: aliases ?? null
            })
        })
    }catch(_err){
        // network error — silent fail
    }
}

export const updateJudge = async(id, newName)=>{
    try{
        await fetch(`${domain}/api/v1/admin/update-judge`,{
            method: 'POST',
            credentials: 'include',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                id: id,
                newName: newName
            })
        })
    }catch(_err){
        // network error — silent fail
    }
}

export const deleteScore = async(id)=>{
    try{
        await fetch(`${domain}/api/v1/admin/score`,{
            method: 'DELETE',
            credentials: 'include',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                event_id: id
            })
        })
    }catch(_err){
        // network error — silent fail
    }
}

export const getAllImages = async() =>{
    try{
        const res = await fetch(`${domain}/api/v1/battle/images`,{
          credentials: 'include'
        })
        if(res.ok){
          return await res.json()
        }
      }catch(_e){
        // network error — returns undefined
    }
}

export const deleteImage = async(name)=>{
    try{
        return await fetch(`${domain}/api/v1/battle/image`,{
            method: 'DELETE',
            credentials: 'include',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                name: name
            })
        })
    }catch(_err){
        // network error — caller handles undefined return
    }
}

export const getFeedbackGroups = async () => {
    try {
        const res = await fetch(`${domain}/api/v1/admin/feedback-groups`, { credentials: 'include' })
        if (res.ok) return await res.json()
        return []
    } catch (_err) { return [] }
}

export const addFeedbackGroup = async (name) => {
    try {
        return await fetch(`${domain}/api/v1/admin/feedback-group`, {
            method: 'POST',
            credentials: 'include',
            headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
            body: JSON.stringify({ name })
        })
    } catch (_err) { /* network error — caller handles undefined return */ }
}

export const deleteFeedbackGroup = async (id) => {
    try {
        return await fetch(`${domain}/api/v1/admin/feedback-group`, {
            method: 'DELETE',
            credentials: 'include',
            headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
            body: JSON.stringify({ id })
        })
    } catch (_err) { /* network error — caller handles undefined return */ }
}

export const addFeedbackTag = async (groupId, label) => {
    try {
        return await fetch(`${domain}/api/v1/admin/feedback-tag`, {
            method: 'POST',
            credentials: 'include',
            headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
            body: JSON.stringify({ groupId, label })
        })
    } catch (_err) { /* network error — caller handles undefined return */ }
}

export const deleteFeedbackTag = async (id) => {
    try {
        return await fetch(`${domain}/api/v1/admin/feedback-tag`, {
            method: 'DELETE',
            credentials: 'include',
            headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
            body: JSON.stringify({ id })
        })
    } catch (_err) { /* network error — caller handles undefined return */ }
}
