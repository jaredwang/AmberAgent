import subprocess,json,sys,time
from pathlib import Path
root=Path(__file__).resolve().parents[3]
base=root/'.workflow/android-modularization-20261018'
adb=['/opt/homebrew/bin/adb','-s','emulator-5580']
def shell(*args):return subprocess.check_output(adb+['shell',*args],text=True).strip()
before={'size':shell('wm','size'),'density':shell('wm','density'),'font':shell('settings','get','system','font_scale')}
suffix=sys.argv[1] if len(sys.argv)>1 else 'final'
selected=set(sys.argv[2:])
result={'before':before,'gates':[]}
try:
 shell('wm','size','840x2400');shell('settings','put','system','font_scale','1.5')
 for name,klass,method,folder,optin in [
 ('council','CouncilModularizationDeviceTest','realCouncilHistoryRetainsTimelineReferencesAttachmentsAndReadonlyQuestion','council','amberCouncilFixture'),
 ('deepread','DeepReadModularizationDeviceTest','realCachedReaderRetainsPartialAndReadyArticlesAcrossReopenWithoutSchedulingWork','deepread','amberDeepReadFixture'),
 ('live','LiveModularizationDeviceTest','realSavedCardHistoryAndConfigurationPersistWithoutStartingCompanion','live','amberLiveFixture'),
 ('terminal-workspace','TerminalWorkspaceModularizationDeviceTest','realAndroidShellOutputStopAndFreshJobReleaseTheWorkspaceLease','terminal-workspace','amberTerminalWorkspaceFixture')]:
  if selected and name not in selected:continue
  cmd=[sys.executable,str(base/'fixtures/run_device_gate.py'),'phase-4-'+name+'-320-large-font-'+suffix,'app.amber.agent.'+klass+'#'+method,'modularization-phase-4-'+folder,'--argument',optin+'=true']
  r=subprocess.run(cmd,cwd=root)
  result['gates'].append({'name':name,'exitCode':r.returncode})
  if r.returncode:break
finally:
 override=next((line.split(':',1)[1].strip() for line in before['size'].splitlines() if line.startswith('Override size:')),None)
 shell('wm','size',override or 'reset')
 if before['font']=='null':shell('settings','delete','system','font_scale')
 else:shell('settings','put','system','font_scale',before['font'])
 result['after']={'size':shell('wm','size'),'density':shell('wm','density'),'font':shell('settings','get','system','font_scale')}
 result['exactRestored']=result['before']==result['after']
 (base/('evidence/phase-4-narrow-device-configuration-'+suffix+'.json')).write_text(json.dumps(result,indent=2)+'\n')
 print(json.dumps(result),flush=True)
sys.exit(0 if result['exactRestored'] and all(v['exitCode']==0 for v in result['gates']) else 1)
