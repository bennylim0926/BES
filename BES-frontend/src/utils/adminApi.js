const domain = ""

export { setOrganiserTier } from '@/utils/api'

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
        const res = await fetch(`${domain}/api/v1/event/feedback-groups`, { credentials: 'include' })
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

export const createOrganiser = async (username, password) => {
    try {
        const res = await fetch(`${domain}/api/v1/admin/organisers`, {
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
            body: JSON.stringify({ username, password })
        })
        if (res.ok) return { ok: true }
        // Extract error from response body — try multiple Spring Boot formats
        const body = await res.json().catch(() => ({}))
        const msg = body?.properties
            ? Object.values(body.properties)[0]           // ProblemDetail (Spring Boot 3.x)
            : body?.errors?.[0]?.defaultMessage           // BindingResult (Spring Boot 2.x)
            || body?.message                               // custom { message: "..." }
            || body?.detail                                // ProblemDetail fallback
            || body?.error                                 // BasicErrorController
            || 'Failed to create organiser.'
        return { ok: false, error: typeof msg === 'string' ? msg : 'Failed to create organiser.' }
    } catch (_err) {
        return { ok: false, error: 'Unable to reach server. Check your connection.' }
    }
}

export const deleteOrganiser = async (accountId) => {
    try {
        return await fetch(`${domain}/api/v1/admin/organisers/${accountId}`, {
            method: 'DELETE',
            credentials: 'include'
        })
    } catch (_err) { /* network error */ }
}

export const getOrganisers = async () => {
    try {
        const res = await fetch(`${domain}/api/v1/admin/organisers`, { credentials: 'include' })
        if (res.ok) return await res.json()
        return []
    } catch (_err) { return [] }
}

export const assignOrganiserToEvent = async (accountId, eventId) => {
    try {
        return await fetch(`${domain}/api/v1/admin/organisers/assign`, {
            method: 'POST',
            credentials: 'include',
            headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
            body: JSON.stringify({ accountId, eventId })
        })
    } catch (_err) { /* network error — caller handles undefined return */ }
}

export const removeOrganiserFromEvent = async (accountId, eventId) => {
    try {
        return await fetch(`${domain}/api/v1/admin/organisers/assign`, {
            method: 'DELETE',
            credentials: 'include',
            headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
            body: JSON.stringify({ accountId, eventId })
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
