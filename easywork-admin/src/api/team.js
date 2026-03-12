import http from './http'

export function getTeams() {
  return http.get('/admin/teams')
}

export function createTeam(data) {
  return http.post('/admin/teams', data)
}

export function addTeamMembers(id, userIds) {
  return http.post(`/admin/teams/${id}/members`, { userIds })
}

export function removeTeamMember(teamId, userId) {
  return http.delete(`/admin/teams/${teamId}/members/${userId}`)
}
