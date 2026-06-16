// const domain = "http://localhost:5050"
const domain = ""

export const login = async (username, password) =>{
  try{
    return await fetch(`${domain}/api/v1/auth/login`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
          username: username,
          password: password,
      })
    })
  }catch(err){
    console.log(err)
  }
}

export const logout = async () =>{
  try{
    return await fetch(`${domain}/api/v1/auth/logout`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      }
    })
  }catch(err){
    console.log(err)
  }
}

export const whoami = async () =>{
  try{
    const res =  await fetch(`${domain}/api/v1/auth/me`,{
      credentials: 'include'
    })
    return await res.json()
  }catch(err){
    console.log(err)
    return { authenticated: false }
  }
}

export const fetchAllFolderEvents = async () =>{
    try{
        const res = await fetch(`${domain}/api/v1/folders`,{
          method: 'GET',
          credentials: 'include'
        })
        return res.ok ? await res.json() : []
      }catch(err){
        console.log(err)
        return []
      }
}

export const fetchAllEvents = async () =>{
  try{
      const res = await fetch(`${domain}/api/v1/event/events`,{
        method: 'GET',
        credentials: 'include'
      })
      return res.ok ? await res.json() : []
    }catch(err){
      console.log(err)
      return []
    }
}

export const checkTableExist = async (eventName, tableExist) =>{
  try{
    const res = await fetch(`${domain}/api/v1/event/${eventName.value}`,{
      credentials: 'include'
    })
    tableExist.value = await res.json();
  }catch(e){
      console.log(e)
  }
}

export const getFileId = async (folderId) =>{
  try{
    const res = await fetch(`${domain}/api/v1/files/${folderId}`,{
      credentials: 'include'
    })
    const result = await res.json()
    if(result.length === 0){
      return null
    }
    return result[0].fileId
  }catch(e){
      console.log(e)
  }
}

export const getResponseDetails = async(fileId) =>{
  try{
      const res = await fetch(`${domain}/api/v1/sheets/participants/breakdown/${fileId}`,{
        credentials: 'include'
      })
      if (!res.ok) throw new Error('Failed to read')
      return await res.json()
  }catch(err){
    console.log(err)
    return null
  }
}

export const getSheetSize = async(fileId) =>{
  try{
      const res = await fetch(`${domain}/api/v1/sheets/participants/size/${fileId}`,{
        credentials: 'include'
      })
      if (!res.ok) throw new Error('Failed to read')
      return await res.json()
  }catch(err){
    console.log(err)
    return null
  }
}

// 
export const getVerifiedParticipantsByEvent = async(eventName) =>{
  try{
    const res = await fetch(`${domain}/api/v1/event/verified-participant/${eventName}`,{
      credentials: 'include'
    })
    return res.ok ? await res.json() : []
  }catch(e){
      console.log(e)
      return []
  }
}

export const getRegisteredParticipantsByEvent = async(eventName) =>{
  try{
    const res = await fetch(`${domain}/api/v1/event/participants/${eventName}`,{
      credentials: 'include',
    })
    return res.ok ? await res.json() : []
  }catch(e){
      console.log(e)
      return []
  }
}

export const getAllJudges = async() =>{
  try{
    const res = await fetch(`${domain}/api/v1/event/judges`,{
      credentials: 'include'
    })
    return res.ok ? await res.json() : []
  }catch(err){
    console.log(err)
    return []
  }
}

export const getEventJudges = async(eventName) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/judges`, { credentials: 'include' })
    return res.ok ? await res.json() : []
  } catch(err) { console.log(err); return [] }
}

export const addEventJudge = async(eventName, judgeName) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/judge`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ judgeName })
    })
  } catch(err) { console.log(err) }
}

export const removeEventJudge = async(eventName, judgeId) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/judge/${judgeId}`, {
      method: 'DELETE',
      credentials: 'include'
    })
  } catch(err) { console.log(err) }
}

export const getJudgesByEvent = async (eventName) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/judges`, { credentials: 'include', headers: { 'Accept': 'application/json' } })
    return res.ok ? await res.json() : []
  } catch { return [] }
}

export const getJudgeDivisions = async (eventName, judgeId) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/judge/${judgeId}/divisions`, { credentials: 'include', headers: { 'Accept': 'application/json' } })
    return res.ok ? await res.json() : []
  } catch { return [] }
}

export const addJudgeToEvent = async (eventName, judgeName) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/judge`, {
      method: 'POST', credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ judgeName })
    })
  } catch { return null }
}

export const assignJudgeToDivision = async (eventName, divisionId, judgeId) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/divisions/${divisionId}/assign-judge/${judgeId}`, {
      method: 'POST', credentials: 'include',
      headers: { 'Accept': 'application/json' }
    })
  } catch { return null }
}

export const getJudgesByDivision = async (eventName, divisionId) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/divisions/${divisionId}/judges`, { credentials: 'include', headers: { 'Accept': 'application/json' } })
    return res.ok ? await res.json() : []
  } catch { return [] }
}

export const addJudgeToDivision = async (eventName, divisionId, judgeName) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/divisions/${divisionId}/judge`, {
      method: 'POST', credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ judgeName })
    })
  } catch { return null }
}

export const removeJudgeFromDivision = async (eventName, divisionId, judgeId) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/divisions/${divisionId}/judge/${judgeId}`, {
      method: 'DELETE', credentials: 'include', headers: { 'Accept': 'application/json' }
    })
  } catch { return null }
}

export const insertPaymentColumnInSheet = async (fileId) =>{
  try{
    const res = await fetch(`${domain}/api/v1/sheets/payment-status`, {
      method: 'POST',
      credentials: 'include',
      headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/json'
      },
      body: JSON.stringify({
          fileId: fileId,
      })
    })
    return res.ok
  }catch(e){
      console.log(e)
      return false
  }
}

export const insertEventInTable = async (eventName, paymentRequired = false) =>{
  try{
    const res = await fetch(`${domain}/api/v1/event`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
          eventName: eventName,
          paymentRequired: paymentRequired,
      })
    })
    return res.ok
  }catch(e){
      console.log(e)
      return false
  }
}

export const addCategoryToEvent = async(eventName, _divisions) =>{
  try{
    return await fetch(`${domain}/api/v1/event/category`, {
      method: 'POST',
      credentials: 'include',
      headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/json'
      },
      body: JSON.stringify({
          eventName: eventName,
          categories: [{name: eventName}]
      })
  })
  }catch(e){
      console.log(e)
      return null
  }
}

export const addParticipantToSystem = async (fileId, eventName)=>{
  try{
  return await fetch(`${domain}/api/v1/event/participants/`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
        fileId: fileId,
        eventName: eventName,
    })
  })
  }catch(e){
    console.log(e)
  }
}

export const addWalkinToSystem = async (participantName, eventName, categoryName, judgeName, teamMembers = [], teamName = '', entryMode = 'team')=>{
  try{
  return await fetch(`${domain}/api/v1/event/walkins/`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
        name: participantName,
        eventName: eventName,
        category: categoryName,
        judgeName: judgeName,
        teamMembers: teamMembers,
        teamName: teamName,
        entryMode: entryMode
    })
  })
  }catch(e){
    console.log(e)
  }
}

export const submitParticipantScore = async (eventName, categoryName, judgeName, participants) =>{
  try{
    return await fetch(`${domain}/api/v1/event/scores`,{
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        eventName: eventName,
        categoryName: categoryName,
        judgeName : judgeName,
        participantScore: participants
      })
    })
  }catch(e){
    console.log(e)
  }
}

export const getParticipantScore = async(eventName) =>{
    try{
      const res = await fetch(`${domain}/api/v1/event/scores/${eventName}`,{
        credentials: 'include'
      })
      if(res.ok){
          return await res.json()
      }
      return []
    }catch(e){
        console.log(e)
        return []
    }
}

export const resetJudgeScores = async (eventName, categoryName, judgeName) => {
  const params = new URLSearchParams({ eventName, categoryName, judgeName })
  try {
    return await fetch(`${domain}/api/v1/event/scores/reset?${params}`, {
      method: 'DELETE',
      credentials: 'include'
    })
  } catch (e) { console.log(e) }
}

export const resetJudgeFeedback = async (eventName, categoryName, judgeName) => {
  const params = new URLSearchParams({ eventName, categoryName, judgeName })
  try {
    return await fetch(`${domain}/api/v1/event/feedback/reset?${params}`, {
      method: 'DELETE',
      credentials: 'include'
    })
  } catch (e) { console.log(e) }
}

export const getBattleJudges = async(eventName = '') =>{
  try{
    const url = eventName
      ? `${domain}/api/v1/battle/judges?event=${encodeURIComponent(eventName)}`
      : `${domain}/api/v1/battle/judges`
    const res = await fetch(url,{
      credentials: 'include'
    })
    if(res.ok){
      return await res.json()
    }
  }catch(e){
    console.log(e)
  }
}

export const getCurrentBattlePair = async(eventName = '')=>{
  try{
    const url = eventName
      ? `${domain}/api/v1/battle/battle-pair?event=${encodeURIComponent(eventName)}`
      : `${domain}/api/v1/battle/battle-pair`
    const res = await fetch(url,{
      credentials: 'include'
    })
    return res.ok ? await res.json() : null
  }catch(_e){
    return null
  }
}

export const battleJudgeVote = async(id, vote, eventName = '') =>{
  try{
    return await fetch(`${domain}/api/v1/battle/vote`,{
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        id: Number(id),
        vote: Number(vote),
        ...(eventName ? { eventName } : {})
      })
    })
  }catch(e){
    console.log(e)
    return null
  }
}

export const addBattleJudge = async(id, weightage = 1, eventName = '') =>{
  try{
    return await fetch(`${domain}/api/v1/battle/judge`,{
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        id: Number(id),
        weightage: Math.max(1, Number(weightage) || 1),
        ...(eventName ? { eventName } : {})
      })
    })
  }catch(e){
    console.log(e)
    return null
  }
}

export const updateJudgeWeightage = async(id, weightage, eventName = '') =>{
  try{
    return await fetch(`${domain}/api/v1/battle/judge/weightage`,{
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        id: Number(id),
        weightage: Math.max(1, Number(weightage) || 1),
        ...(eventName ? { eventName } : {})
      })
    })
  }catch(e){
    console.log(e)
    return null
  }
}

export const removeBattleJudge = async(id, eventName = '') =>{
  try{
    return await fetch(`${domain}/api/v1/battle/judge`,{
      method: 'DELETE',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        id: Number(id),
        ...(eventName ? { eventName } : {})
      })
    })
  }catch(e){
    console.log(e)
    return null
  }
}
export const setBattlePair = async(leftBattler, rightBattler, isFinal = false, leftMembers = [], rightMembers = [], eventName = '') =>{
  try{
    return await fetch(`${domain}/api/v1/battle/battle-pair`,{
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        leftBattler: leftBattler,
        rightBattler: rightBattler,
        isFinal: isFinal,
        leftMembers: leftMembers,
        rightMembers: rightMembers,
        ...(eventName ? { eventName } : {})
      })
    })
  }catch(e){
    console.log(e)
    return null
  }
}

export const clearBattlePair = async (eventName = '') => {
  try {
    const url = eventName
      ? `${domain}/api/v1/battle/battle-pair?event=${encodeURIComponent(eventName)}`
      : `${domain}/api/v1/battle/battle-pair`
    return await fetch(url, {
      method: 'DELETE',
      credentials: 'include',
      headers: { 'Accept': 'application/json' }
    })
  } catch (e) {
    console.log(e)
    return null
  }
}

export const setBattleScore = async (isFinal = false, eventName = '') => {
  try {
    return await fetch(`${domain}/api/v1/battle/score`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ isFinal, ...(eventName ? { eventName } : {}) })
    })
  } catch (e) {
    console.log(e)
    return null
  }
}

export const resetBattleVotes = async (eventName = '') => {
  try {
    return await fetch(`${domain}/api/v1/battle/revote`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: eventName ? JSON.stringify({ eventName }) : undefined
    })
  } catch (e) {
    console.log(e)
    return null
  }
}

export const revealChampion = async (categoryName, championName, eventName = '') => {
  try {
    return await fetch(`${domain}/api/v1/battle/champion-reveal`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ categoryName, championName, dismiss: false, ...(eventName ? { eventName } : {}) })
    })
  } catch (e) {
    console.log(e)
    return null
  }
}

export const getBattleChampions = async (eventName) => {
  try {
    const res = await fetch(`${domain}/api/v1/battle/champions?event=${encodeURIComponent(eventName)}`, {
      credentials: 'include',
      headers: { 'Accept': 'application/json' }
    })
    return res.ok ? res.json() : {}
  } catch (e) {
    console.log(e)
    return {}
  }
}

export const dismissChampionReveal = async (eventName = '') => {
  try {
    return await fetch(`${domain}/api/v1/battle/champion-reveal`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ dismiss: true, ...(eventName ? { eventName } : {}) })
    })
  } catch (e) {
    console.log(e)
    return null
  }
}

export const setBracketState = async (rounds, topSize, currentRoundIndex = 0, eventName = '') => {
  try {
    return await fetch(`${domain}/api/v1/battle/bracket`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ rounds, topSize: String(topSize), currentRoundIndex, ...(eventName ? { eventName } : {}) })
    })
  } catch (e) { console.error(e) }
}

export const getBracketState = async (eventName = '') => {
  try {
    const url = eventName
      ? `${domain}/api/v1/battle/bracket?event=${encodeURIComponent(eventName)}`
      : `${domain}/api/v1/battle/bracket`
    const res = await fetch(url, { credentials: 'include' })
    return res.ok ? await res.json() : null
  } catch (_e) { return null }
}

export const setActiveCategory = async (eventName, categoryName) => {
  try {
    return await fetch(`${domain}/api/v1/battle/active-category`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ eventName, categoryName })
    })
  } catch (e) { console.error(e) }
}


export const getBattleState = async (eventName = '') => {
  try {
    const url = eventName
      ? `${domain}/api/v1/battle/state?event=${encodeURIComponent(eventName)}`
      : `${domain}/api/v1/battle/state`
    const res = await fetch(url, { credentials: 'include' })
    return res.ok ? await res.json() : null
  } catch (_e) { return null }
}

export const getCategoryStateFromDb = async (eventName, categoryName) => {
  try {
    const res = await fetch(
      `${domain}/api/v1/battle/category-state?event=${encodeURIComponent(eventName)}&category=${encodeURIComponent(categoryName)}`,
      { credentials: 'include' }
    )
    return res.ok ? await res.json() : null
  } catch (_e) { return null }
}

export const uploadBattleLogo = async (eventName, file) => {
  try {
    const formData = new FormData()
    formData.append('file', file)
    const url = eventName
      ? `/api/v1/battle/logo-upload?event=${encodeURIComponent(eventName)}`
      : '/api/v1/battle/logo-upload'
    return await fetch(`${domain}${url}`, {
      method: 'POST',
      credentials: 'include',
      body: formData,
    })
  } catch (e) {
    console.log(e)
    return null
  }
}

export const deleteBattleLogo = async (eventName) => {
  try {
    const url = eventName
      ? `/api/v1/battle/logo?event=${encodeURIComponent(eventName)}`
      : '/api/v1/battle/logo'
    return await fetch(`${domain}${url}`, {
      method: 'DELETE',
      credentials: 'include',
    })
  } catch (e) {
    console.log(e)
    return null
  }
}

export const uploadImage = async(file)=>{
  try{
    const formData = new FormData();
    formData.append("file", file);
    return await fetch(`${domain}/api/v1/battle/upload`,{
      method: 'POST',
      credentials: 'include',
      body: formData
    })
  }catch(e){
    console.log(e)
    return null
  }
}

export const getImage = async (filename) => {
  try {
    const res = await fetch(`${domain}/api/v1/battle/uploads/${encodeURIComponent(filename)}`, {
      method: 'GET',
      credentials: 'include',
    });

    if (!res.ok) {
      throw new Error(`Failed to fetch image: ${res.status}`);
    }

    // Convert the image stream into a blob
    const blob = await res.blob();

    // Create a temporary local URL for use in <img>
    const imageUrl = URL.createObjectURL(blob);

    return imageUrl;
  } catch (err) {
    console.error('Error fetching image:', err);
    return null;
  }
};

export const getBattlePhase = async (eventName = '') => {
  try {
    const url = eventName
      ? `${domain}/api/v1/battle/phase?event=${encodeURIComponent(eventName)}`
      : `${domain}/api/v1/battle/phase`
    const res = await fetch(url, { credentials: 'include' })
    return res.ok ? await res.json() : { phase: 'IDLE' }
  } catch (_e) { return { phase: 'IDLE' } }
}

export const setBattlePhase = async (phase, champion, eventName = '') => {
  try {
    return await fetch(`${domain}/api/v1/battle/phase`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ phase, ...(champion ? { champion } : {}), ...(eventName ? { eventName } : {}) })
    })
  } catch (e) { console.log(e) }
}

export const getSmokeList = async()=>{
  try{
    const res = await fetch(`${domain}/api/v1/battle/smoke`,{
      credentials: 'include'
    })
    if(res.ok){
        return await res.json()
    }
  }catch(e){
      console.log(e)
  }
}
export const getEmailTemplate = async (eventName) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/email-template`, {
      credentials: 'include'
    })
    if (!res.ok) return null
    return await res.json()
  } catch (e) {
    console.log(e)
  }
}

export const updateEmailTemplate = async (eventName, subject, body) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/email-template`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ subject, body })
    })
  } catch (e) {
    console.log(e)
  }
}

export const resetEmailTemplate = async (eventName) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/email-template/reset`, {
      method: 'POST',
      credentials: 'include'
    })
    if (res.ok) return await res.json()
    return null
  } catch (e) {
    console.log(e)
  }
}

export const getUnverifiedParticipants = async (fileId) => {
  try {
    const res = await fetch(`${domain}/api/v1/sheets/participants/unverified/${fileId}`, {
      credentials: 'include'
    })
    if (res.ok) return await res.json()
    return []
  } catch (e) {
    console.log(e)
    return []
  }
}

export const getUnverifiedParticipantsDB = async (eventName) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/unverified-participants`, {
      credentials: 'include'
    })
    if (res.ok) return await res.json()
    return []
  } catch (e) {
    console.log(e)
    return []
  }
}

export const verifyPayment = async (participantId, eventId) => {
  try {
    return await fetch(`${domain}/api/v1/event/participants/verify-payment`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ participantId, eventId })
    })
  } catch (e) {
    console.log(e)
  }
}

export const verifyPaymentBatch = async (list) => {
  try {
    return await fetch(`${domain}/api/v1/event/participants/verify-payment-batch`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify(list)
    })
  } catch (e) {
    console.log(e)
  }
}

export const getCategoriesByEvent = async (eventName) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/categories`, {
      credentials: 'include'
    })
    if (res.ok) return await res.json()
    return []
  } catch (e) {
    console.log(e)
    return []
  }
}

export const removeParticipantCategory = async (participantId, eventId, categoryId) => {
  try {
    return await fetch(`${domain}/api/v1/event/participant-category/${participantId}/${eventId}/${categoryId}`, {
      method: 'DELETE',
      credentials: 'include'
    })
  } catch (e) {
    console.log(e)
  }
}

export const deleteParticipantFromEvent = async (participantId, eventId) => {
  try {
    return await fetch(`${domain}/api/v1/event/participant/${participantId}/${eventId}`, {
      method: 'DELETE',
      credentials: 'include'
    })
  } catch (e) {
    console.log(e)
  }
}

export const updateParticipant = async (participantId, eventId, { name, memberNames = [] }) => {
  try {
    return await fetch(`${domain}/api/v1/event/participant/${participantId}/${eventId}`, {
      method: 'PUT',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, memberNames })
    })
  } catch (e) {
    console.log(e)
  }
}

export const updateParticipantsJudge = async (eventId, updatedList) => {
  try {
    return await fetch(`${domain}/api/v1/event/participants-judge/`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ updatedList })
    })
  } catch (e) {
    console.log(e)
  }
}

export const addCategoryToParticipant = async (participantId, eventId, categoryName, entryMode, teamName, teamMembers) => {
  try {
    return await fetch(`${domain}/api/v1/event/participant-category`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ participantId, eventId, categoryName, entryMode, teamName, teamMembers })
    })
  } catch (e) {
    console.log(e)
  }
}

export const updateSmokeList  = async(battlers, eventName = '')=>{
  try{
    return await fetch(`${domain}/api/v1/battle/smoke`,{
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        battlers: battlers,
        ...(eventName ? { eventName } : {})
      })
    })
  }catch(e){
    console.log(e)
    return null
  }
}


export const getJudgingMode = async (eventName) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/judging-mode/${encodeURIComponent(eventName)}`, {
      credentials: 'include'
    })
    if (res.ok) return await res.json()
    return null
  } catch (e) {
    console.log(e)
    return null
  }
}

export const setJudgingMode = async (eventName, mode) => {
  try {
    return await fetch(`${domain}/api/v1/event/judging-mode`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ eventName, judgingMode: mode })
    })
  } catch (e) {
    console.log(e)
  }
}

export const getFeedbackEnabled = async (eventName) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/feedback-enabled/${encodeURIComponent(eventName)}`, {
      credentials: 'include'
    })
    if (res.ok) return await res.json()
    return null
  } catch (e) {
    console.log(e)
    return null
  }
}

export const setFeedbackEnabled = async (eventName, enabled) => {
  try {
    return await fetch(`${domain}/api/v1/event/feedback-enabled`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ eventName, feedbackEnabled: enabled })
    })
  } catch (e) {
    console.log(e)
  }
}

export const submitAuditionFeedback = async (eventName, categoryName, judgeName, auditionNumber, tagIds, note) => {
  try {
    return await fetch(`${domain}/api/v1/event/feedback`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ eventName, categoryName, judgeName, auditionNumber, tagIds, note })
    })
  } catch (e) {
    console.log(e)
  }
}

export const getAuditionFeedback = async (eventName, categoryName, judgeName, auditionNumber) => {
  try {
    const params = new URLSearchParams({ eventName, categoryName, judgeName, auditionNumber })
    const res = await fetch(`${domain}/api/v1/event/feedback?${params}`, { credentials: 'include' })
    if (res.ok) return await res.json()
    return null
  } catch (e) {
    console.log(e)
    return null
  }
}

export const getParticipantFeedback = async (eventName, categoryName, participantName) => {
  try {
    const params = new URLSearchParams({ eventName, categoryName, participantName })
    const res = await fetch(`${domain}/api/v1/event/feedback/participant?${params}`, { credentials: 'include' })
    if (res.ok) return await res.json()
    return []
  } catch (e) {
    console.log(e)
    return []
  }
}

export const getResultsStatus = async (eventName) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/results-status`, { credentials: 'include' })
    if (res.ok) return await res.json()
    return null
  } catch (e) {
    console.log(e)
    return null
  }
}

export const releaseResults = async (eventName, released) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/release-results`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ released })
    })
    if (res.ok) return await res.json()
    return null
  } catch (e) {
    console.log(e)
    return null
  }
}

export const getParticipantRefs = async (eventName) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/participant-refs`, { credentials: 'include' })
    if (res.ok) return await res.json()
    return []
  } catch (e) {
    console.log(e)
    return []
  }
}

export const getScoringCriteria = async (eventName, categoryName) => {
  try {
    const params = categoryName ? `?category=${encodeURIComponent(categoryName)}` : ''
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/criteria${params}`, {
      credentials: 'include'
    })
    if (res.ok) return await res.json()
    return []
  } catch (e) {
    console.log(e)
    return []
  }
}

export const getScoringCriteriaStrict = async (eventName, categoryName) => {
  try {
    const base = `${domain}/api/v1/event/${encodeURIComponent(eventName)}/criteria?strict=true`
    const url = categoryName ? `${base}&category=${encodeURIComponent(categoryName)}` : base
    const res = await fetch(url, { credentials: 'include' })
    if (res.ok) return await res.json()
    return []
  } catch (e) {
    console.log(e)
    return []
  }
}

export const addScoringCriteria = async (eventName, payload) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/criteria`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    if (res.ok) return await res.json()
    return null
  } catch (e) {
    console.log(e)
    return null
  }
}

export const updateScoringCriteria = async (eventName, criteriaId, payload) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/criteria/${criteriaId}`, {
      method: 'PUT',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    if (res.ok) return await res.json()
    return null
  } catch (e) {
    console.log(e)
    return null
  }
}

export const deleteScoringCriteria = async (eventName, criteriaId) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/criteria/${criteriaId}`, {
      method: 'DELETE',
      credentials: 'include'
    })
    return res.ok
  } catch (e) {
    console.log(e)
    return false
  }
}

export const deleteAllCriteriaForCategory = async (eventName, categoryName) => {
  try {
    const params = categoryName ? `?category=${encodeURIComponent(categoryName)}` : ''
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/criteria${params}`, {
      method: 'DELETE',
      credentials: 'include'
    })
    return res.ok
  } catch (e) {
    console.log(e)
    return false
  }
}

export const getResultsByRefCode = async (refCode) => {
  try {
    const res = await fetch(`${domain}/api/v1/results?ref=${encodeURIComponent(refCode)}`)
    if (res.ok) return await res.json()
    const body = await res.json().catch(() => ({}))
    return { error: body.error || 'Results not found or not yet released' }
  } catch (e) {
    console.log(e)
    return { error: 'Network error' }
  }
}
export const getPickupCrews = async (eventName, categoryName) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/crews/${encodeURIComponent(eventName)}/${encodeURIComponent(categoryName)}`, {
      credentials: 'include'
    })
    if (res.ok) return await res.json()
    return []
  } catch (e) {
    console.log(e)
    return []
  }
}

export const createPickupCrew = async (eventName, categoryName, crewName, memberParticipantIds) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/crews`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ eventName, categoryName, crewName, memberParticipantIds })
    })
    if (res.ok) return await res.json()
    const body = await res.json().catch(() => ({}))
    return { error: body.error || 'Failed to create crew' }
  } catch (e) {
    console.log(e)
    return { error: 'Network error' }
  }
}

export const deletePickupCrew = async (crewId) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/crews/${crewId}`, {
      method: 'DELETE',
      credentials: 'include'
    })
    return res.ok
  } catch (e) {
    console.log(e)
    return false
  }
}

export const updateEventCategoryFormat = async (eventName, eventCategoryId, format) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/categories/${eventCategoryId}/format`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ format })
    })
    return res.ok
  } catch (e) {
    console.log(e)
    return false
  }
}

export const sendHeartbeat = async () => {
  try {
    await fetch(`${domain}/api/v1/auth/heartbeat`, { method: 'POST', credentials: 'include' })
  } catch (_) { /* fire-and-forget */ }
}

export const updateCategoryRoundLabel = async (eventName, eventCategoryId, roundLabel) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/categories/${eventCategoryId}/round-label`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ roundLabel })
    })
    return res.ok
  } catch (e) {
    console.error(e)
    return false
  }
}

export const updateCategoryNumberColor = async (eventName, eventCategoryId, numberColor) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/categories/${eventCategoryId}/number-color`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ numberColor })
    })
    return res.ok
  } catch (e) {
    console.error(e)
    return false
  }
}

export const getOverlayConfig = async (eventName = '') => {
  try {
    const url = eventName
      ? `${domain}/api/v1/battle/overlay-config?event=${encodeURIComponent(eventName)}`
      : `${domain}/api/v1/battle/overlay-config`
    const res = await fetch(url, {
      credentials: 'include',
    })
    return res.ok ? await res.json() : { showImages: true, leftColor: '#dc2626', rightColor: '#2563eb' }
  } catch (err) {
    console.log(err)
    return { showImages: true, leftColor: '#dc2626', rightColor: '#2563eb' }
  }
}

export const setOverlayConfig = async (config, eventName = '') => {
  try {
    return await fetch(`${domain}/api/v1/battle/overlay-config`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ ...config, ...(eventName ? { eventName } : {}) }),
    })
  } catch (err) {
    console.log(err)
  }
}

export const getCheckinList = async (eventName) => {
  try {
    return await fetch(`${domain}/api/v1/event/${eventName}/checkin-list`, {
      credentials: 'include'
    })
  } catch (e) {
    console.log(e)
  }
}

export const checkInParticipant = async (participantId, eventId) => {
  try {
    return await fetch(`${domain}/api/v1/event/register-participant/${participantId}/${eventId}`, {
      credentials: 'include'
    })
  } catch (e) {
    console.log(e)
  }
}

export const sendCheckinPreview = async (eventName, previewData) => {
  try {
    return await fetch(`${domain}/api/v1/event/${eventName}/checkin-preview`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(previewData)
    })
  } catch (e) {
    console.log(e)
  }
}

export const getCheckinPreviews = async (eventName) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${eventName}/checkin-preview`, { credentials: 'include' })
    if (res.ok) return await res.json()
    return []
  } catch { return [] }
}

export const getBattleGuests = async (eventName) => {
  try {
    return await fetch(`${domain}/api/v1/event/battle-guests/${encodeURIComponent(eventName)}`, {
      credentials: 'include'
    })
  } catch (e) {
    console.log(e)
  }
}

export const addBattleGuest = async (eventName, categoryName, guestName, entryRound, memberNames = []) => {
  try {
    return await fetch(`${domain}/api/v1/event/battle-guests/${encodeURIComponent(eventName)}/${encodeURIComponent(categoryName)}`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ guestName, entryRound, memberNames })
    })
  } catch (e) {
    console.log(e)
  }
}

export const removeBattleGuest = async (guestId) => {
  try {
    return await fetch(`${domain}/api/v1/event/battle-guests/${guestId}`, {
      method: 'DELETE',
      credentials: 'include'
    })
  } catch (e) {
    console.log(e)
  }
}

export const getAppConfig = async () => {
  const res = await fetch('/api/v1/config/app', { credentials: 'include' })
  return res.json()
}

export const postAppConfig = async (accentColor) => {
  const res = await fetch('/api/v1/config/app', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ accentColor })
  })
  return res.json()
}

export const addDivision = async (eventName, name, format, categoryId) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/divisions`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, format: format || null, categoryId: categoryId || null })
    })
  } catch (e) { console.log(e) }
}

export const renameDivision = async (eventName, divisionId, name) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/divisions/${divisionId}/name`, {
      method: 'PATCH',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ name })
    })
  } catch (e) { console.log(e) }
}

export const updateDivisionAliases = async (eventName, divisionId, aliases) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/divisions/${divisionId}/aliases`, {
      method: 'PATCH',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ aliases })
    })
  } catch (e) { console.log(e) }
}

export const updateDivisionSoloAllowed = async (eventName, divisionId, soloAllowed) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/divisions/${divisionId}/solo-allowed`, {
      method: 'PATCH',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ soloAllowed })
    })
  } catch (e) { console.log(e) }
}

export const deleteDivision = async (eventName, divisionId) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/divisions/${divisionId}`, {
      method: 'DELETE',
      credentials: 'include'
    })
  } catch (e) { console.log(e) }
}

export const getSheetCategories = async (fileId) => {
  try {
    const res = await fetch(`${domain}/api/v1/sheets/categories/${fileId}`, {
      credentials: 'include',
      headers: { 'Accept': 'application/json' }
    })
    return res.ok ? (await res.json()).values ?? [] : []
  } catch (e) { console.log(e); return [] }
}

export const assignAuditionNumber = async (eventId, participantId, eventCategoryId, auditionNumber) => {
  try {
    return await fetch(`${domain}/api/v1/event/adjust/assign`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ eventId, participantId, eventCategoryId, auditionNumber })
    })
  } catch (e) { console.log(e) }
}

export const assignAuditionNumbersBatch = async (eventId, participantId, assignments) => {
  // assignments: [{ eventCategoryId, auditionNumber }, ...]
  try {
    return await fetch(`${domain}/api/v1/event/adjust/assign-batch`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ eventId, participantId, assignments })
    })
  } catch (e) { console.log(e) }
}

export const swapAuditionNumbers = async (eventId, eventCategoryId, participantId1, participantId2) => {
  try {
    return await fetch(`${domain}/api/v1/event/adjust/swap`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ eventId, eventCategoryId, participantId1, participantId2 })
    })
  } catch (e) { console.log(e) }
}

export const releaseAuditionNumbers = async (eventId, participantId) => {
  try {
    return await fetch(`${domain}/api/v1/event/adjust/release`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ eventId, participantId })
    })
  } catch (e) { console.log(e) }
}

export const redeemToken = async (tokenId) => {
  try {
    const res = await fetch(`${domain}/api/v1/auth/token`, {
      method: 'POST', credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ tokenId })
    })
    if (!res.ok) {
      const body = await res.json().catch(() => null)
      return { authenticated: false, status: res.status, error: body?.message || body?.error || 'Invalid or expired link.' }
    }
    return await res.json()
  } catch (err) {
    console.error(err)
    return { authenticated: false, error: 'Unable to reach server. Check your connection and try again.' }
  }
}

export const generateToken = async (role, eventId, judgeId, expiresInDays = 7) => {
  try {
    const params = new URLSearchParams({ role, eventId, expiresInDays })
    if (judgeId) params.append('judgeId', judgeId)
    const res = await fetch(`${domain}/api/v1/auth/generate-token?${params}`, {
      method: 'POST', credentials: 'include'
    })
    return res.ok ? await res.json() : null
  } catch (err) { console.error(err); return null }
}

export const getSessionTokens = async (eventId) => {
  try {
    const res = await fetch(`${domain}/api/v1/auth/tokens?eventId=${eventId}`, { credentials: 'include' })
    return res.ok ? await res.json() : []
  } catch (err) { console.error(err); return [] }
}

export const revokeSessionToken = async (tokenId) => {
  try {
    return await fetch(`${domain}/api/v1/auth/tokens/${tokenId}`, {
      method: 'DELETE', credentials: 'include'
    })
  } catch (err) { console.error(err) }
}

export const setResolvedParticipants = async (eventName, categoryName, participants) => {
  try {
    return await fetch(`${domain}/api/v1/battle/resolved-participants`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ eventName, categoryName, participants })
    })
  } catch (e) { console.error(e) }
}

export const postAuditionDisplayState = async (state) => {
  try {
    return await fetch(`${domain}/api/v1/event/audition-display`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(state)
    })
  } catch (e) {
    console.error(e)
  }
}

export const getAuditionDisplayState = async (eventName, categoryName) => {
  try {
    const url = `${domain}/api/v1/event/audition-display?event=${encodeURIComponent(eventName)}&category=${encodeURIComponent(categoryName || '')}`
    const res = await fetch(url, { credentials: 'include' })
    if (res.ok) return await res.json()
    return null
  } catch (e) {
    console.error(e)
    return null
  }
}

export const setOrganiserTier = async (accountId, tier) => {
  try {
    const res = await fetch(`${domain}/api/v1/admin/organisers/tier`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ accountId, tier })
    })
    return { ok: res.ok, status: res.status, data: res.ok ? await res.json() : null, error: res.ok ? null : await res.text() }
  } catch (e) {
    console.error(e)
    return null
  }
}
