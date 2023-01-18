Search.setIndex({docnames:["index","modules","olivepy","olivepy.api","olivepy.client","olivepy.messaging","olivepy.utils"],envversion:{"sphinx.domains.c":2,"sphinx.domains.changeset":1,"sphinx.domains.citation":1,"sphinx.domains.cpp":4,"sphinx.domains.index":1,"sphinx.domains.javascript":2,"sphinx.domains.math":2,"sphinx.domains.python":3,"sphinx.domains.rst":2,"sphinx.domains.std":2,sphinx:56},filenames:["index.rst","modules.rst","olivepy.rst","olivepy.api.rst","olivepy.client.rst","olivepy.messaging.rst","olivepy.utils.rst"],objects:{"":{olivepy:[2,0,0,"-"]},"olivepy.api":{olive_async_client:[3,0,0,"-"],oliveclient:[3,0,0,"-"],workflow:[3,0,0,"-"]},"olivepy.api.olive_async_client":{AsyncOliveClient:[3,1,1,""],ClientMonitorThread:[3,1,1,""]},"olivepy.api.olive_async_client.AsyncOliveClient":{add_heartbeat_listener:[3,2,1,""],analyze_frames:[3,2,1,""],analyze_global:[3,2,1,""],analyze_regions:[3,2,1,""],audio_modification:[3,2,1,""],clear_heartbeat_listeners:[3,2,1,""],connect:[3,2,1,""],disconnect:[3,2,1,""],enqueue_request:[3,2,1,""],enroll:[3,2,1,""],get_active:[3,2,1,""],get_status:[3,2,1,""],get_update_status:[3,2,1,""],is_connected:[3,2,1,""],load_plugin_domain:[3,2,1,""],request_plugins:[3,2,1,""],run:[3,2,1,""],setup_multithreading:[3,2,1,""],sync_request:[3,2,1,""],unenroll:[3,2,1,""],unload_plugin_domain:[3,2,1,""],update_plugin_domain:[3,2,1,""]},"olivepy.api.olive_async_client.ClientMonitorThread":{add_event_callback:[3,2,1,""],clear_callbacks:[3,2,1,""],run:[3,2,1,""],stopWorker:[3,2,1,""]},"olivepy.api.oliveclient":{ClientBrokerWorker:[3,1,1,""],OliveClient:[3,1,1,""],get_bit_depth:[3,3,1,""],package_buffer_audio:[3,3,1,""]},"olivepy.api.oliveclient.ClientBrokerWorker":{add_event_callback:[3,2,1,""],enqueueRequest:[3,2,1,""],run:[3,2,1,""],stopWorker:[3,2,1,""]},"olivepy.api.oliveclient.OliveClient":{adapt_supervised:[3,2,1,""],adapt_supervised_old:[3,2,1,""],analyze_frames:[3,2,1,""],analyze_global:[3,2,1,""],analyze_regions:[3,2,1,""],apply_threshold:[3,2,1,""],audio_modification:[3,2,1,""],connect:[3,2,1,""],convert_preprocessed_annotations:[3,2,1,""],disconnect:[3,2,1,""],enroll:[3,2,1,""],finalize_supervised_adaptation:[3,2,1,""],get_active:[3,2,1,""],get_fullobj:[3,2,1,""],get_info:[3,2,1,""],get_status:[3,2,1,""],get_update_status:[3,2,1,""],is_connected:[3,2,1,""],load_plugin_domain:[3,2,1,""],parse_annotation_file:[3,2,1,""],preprocess_supervised_audio:[3,2,1,""],request_plugins:[3,2,1,""],requst_sad_adaptation:[3,2,1,""],setup_multithreading:[3,2,1,""],unenroll:[3,2,1,""],unload_plugin_domain:[3,2,1,""],update_plugin_domain:[3,2,1,""],version:[3,2,1,""]},"olivepy.api.workflow":{OliveWorkflow:[3,1,1,""],OliveWorkflowDefinition:[3,1,1,""],WorkflowException:[3,4,1,""]},"olivepy.api.workflow.OliveWorkflow":{adapt:[3,2,1,""],analyze:[3,2,1,""],enroll:[3,2,1,""],get_analysis_class_ids:[3,2,1,""],get_analysis_job_names:[3,2,1,""],get_analysis_task_info:[3,2,1,""],get_analysis_tasks:[3,2,1,""],get_enrollment_job_names:[3,2,1,""],get_enrollment_tasks:[3,2,1,""],get_unenrollment_job_names:[3,2,1,""],get_unenrollment_tasks:[3,2,1,""],package_audio:[3,2,1,""],package_image:[3,2,1,""],package_text:[3,2,1,""],package_video:[3,2,1,""],serialize_audio:[3,2,1,""],to_json:[3,2,1,""],unenroll:[3,2,1,""]},"olivepy.api.workflow.OliveWorkflowDefinition":{create_workflow:[3,2,1,""],get_json:[3,2,1,""],to_json:[3,2,1,""]},"olivepy.client":{analyze_client:[4,0,0,"-"],enroll_client:[4,0,0,"-"],learn_client:[4,0,0,"-"],status_client:[4,0,0,"-"],utils_client:[4,0,0,"-"],workflow_cem:[4,0,0,"-"],workflow_client:[4,0,0,"-"],workflow_enroll_client:[4,0,0,"-"]},"olivepy.client.analyze_client":{heartbeat_notification:[4,3,1,""]},"olivepy.client.status_client":{heartbeat_notification:[4,3,1,""],print_plugins:[4,3,1,""]},"olivepy.client.utils_client":{main:[4,3,1,""]},"olivepy.client.workflow_cem":{main:[4,3,1,""]},"olivepy.client.workflow_client":{heartbeat_notification:[4,3,1,""],main:[4,3,1,""]},"olivepy.client.workflow_enroll_client":{main:[4,3,1,""],parse_pem_file:[4,3,1,""]},"olivepy.messaging":{msgutil:[5,0,0,"-"],olive_pb2:[5,0,0,"-"],response:[5,0,0,"-"]},"olivepy.messaging.msgutil":{AllowableErrorFromServer:[5,4,1,""],AudioTransferType:[5,1,1,""],ExceptionFromServer:[5,4,1,""],get_uuid:[5,3,1,""],package_audio:[5,3,1,""],serialize_audio:[5,3,1,""]},"olivepy.messaging.msgutil.AudioTransferType":{AUDIO_DECODED:[5,5,1,""],AUDIO_PATH:[5,5,1,""],AUDIO_SERIALIZED:[5,5,1,""]},"olivepy.messaging.response":{OliveClassStatusResponse:[5,1,1,""],OliveServerResponse:[5,1,1,""],OliveWorkflowActualizedResponse:[5,1,1,""],OliveWorkflowAnalysisResponse:[5,1,1,""],OliveWorkflowEnrollmentResponse:[5,1,1,""],get_workflow_job_names:[5,3,1,""],get_workflow_job_tasks:[5,3,1,""],get_workflow_jobs:[5,3,1,""],sort_global_scores:[5,3,1,""]},"olivepy.messaging.response.OliveClassStatusResponse":{get_response_as_json:[5,2,1,""],get_workflow_type:[5,2,1,""],parse_from_response:[5,2,1,""],to_json:[5,2,1,""]},"olivepy.messaging.response.OliveServerResponse":{get_error:[5,2,1,""],get_response:[5,2,1,""],get_response_as_json:[5,2,1,""],get_workflow_type:[5,2,1,""],is_error:[5,2,1,""],is_successful:[5,2,1,""],parse_from_response:[5,2,1,""],to_json:[5,2,1,""]},"olivepy.messaging.response.OliveWorkflowActualizedResponse":{get_analysis_jobs:[5,2,1,""],get_analysis_tasks:[5,2,1,""],get_request_jobs:[5,2,1,""],get_response_as_json:[5,2,1,""],get_workflow:[5,2,1,""],is_allowable_error:[5,2,1,""],parse_from_response:[5,2,1,""],to_json:[5,2,1,""]},"olivepy.messaging.response.OliveWorkflowAnalysisResponse":{get_analysis_job_result:[5,2,1,""],get_analysis_jobs:[5,2,1,""],get_analysis_task_result:[5,2,1,""],get_analysis_tasks:[5,2,1,""],get_failed_tasks:[5,2,1,""],get_request_jobs:[5,2,1,""],get_response_as_json:[5,2,1,""],is_allowable_error:[5,2,1,""],parse_from_response:[5,2,1,""],to_json:[5,2,1,""]},"olivepy.messaging.response.OliveWorkflowEnrollmentResponse":{get_response_as_json:[5,2,1,""],is_allowable_error:[5,2,1,""],parse_from_response:[5,2,1,""],to_json:[5,2,1,""]},"olivepy.utils":{utils:[6,0,0,"-"]},"olivepy.utils.utils":{open_config:[6,3,1,""],parse_file_options:[6,3,1,""],parse_json_options:[6,3,1,""],parse_json_options_as_dict:[6,3,1,""],parse_pem_file:[6,3,1,""]},olivepy:{api:[3,0,0,"-"],client:[4,0,0,"-"],messaging:[5,0,0,"-"],utils:[6,0,0,"-"]}},objnames:{"0":["py","module","Python module"],"1":["py","class","Python class"],"2":["py","method","Python method"],"3":["py","function","Python function"],"4":["py","exception","Python exception"],"5":["py","attribute","Python attribute"]},objtypes:{"0":"py:module","1":"py:class","2":"py:method","3":"py:function","4":"py:exception","5":"py:attribute"},terms:{"0":[3,5,6],"1":[3,5,6],"10":3,"16":[3,5],"2":[3,5],"2618":3,"3":[3,5],"4":3,"5":3,"5588":3,"6":3,"6200":3,"7200":3,"8":3,"8000":3,"9":3,"9500":3,"99":[3,6],"byte":[3,5],"case":[3,5,6],"class":[3,5],"default":5,"do":[3,5],"enum":5,"final":3,"float":3,"function":[3,5],"int":3,"new":[3,5],"return":[3,4,5,6],"throw":5,"true":[3,5],A:[3,5],But:5,For:3,If:[3,5],In:[3,6],NOT:[3,5],Not:3,Such:3,The:[3,5],There:[3,5],These:3,To:3,Will:3,about:3,accept:3,access:5,action:3,activ:3,actual:[3,5],actualized_workflow:3,ad:5,adapt:[3,5],adapt_supervis:3,adapt_supervised_old:3,adapt_workspac:3,add:3,add_event_callback:3,add_heartbeat_listen:3,addit:3,address:3,after:3,all:[3,6],allow:5,allowableerrorfromserv:5,alreadi:[3,5],also:[5,6],altern:3,although:3,an:[3,5],analysi:[3,5],analyz:[3,5],analyze_cli:[1,2],analyze_fram:3,analyze_glob:3,analyze_region:3,ani:3,annot:[3,5],annotations_file_nam:3,anystr:[3,5],api:[1,2,4],applic:3,apply_threshold:3,applyupdaterequest:3,applyupdateresult:3,ar:[3,5,6],arg:3,argument:3,arrai:[3,5],assum:3,async:[3,4],asynchron:3,asyncolivecli:[3,5],audio2:3,audio:[3,4,5,6],audio_data:[3,5],audio_decod:5,audio_input:3,audio_modif:3,audio_msg:5,audio_path:[3,5],audio_seri:[3,5],audiobuff:3,audiomodificationrequest:3,audiomodificationresult:3,audiotransfertyp:[3,5],avail:3,base:[3,5],been:[3,4,5],being:5,belong:[3,5],below:3,binari:[3,5],block:3,bool:3,both:6,buffer:[3,5],c:3,call:3,callabl:3,callback:[3,4],can:[3,5,6],chain:5,chang:3,channel:[3,4,5,6],check:3,class_annot:3,class_id:3,classannot:3,classif:3,classmethod:3,classmodificationresult:3,classremovalresult:3,clear_callback:3,clear_heartbeat_listen:3,clearer:3,client:[1,2,3,5],client_id:3,clientbrokerwork:3,clientmonitorthread:3,close:3,code:3,column:3,compact:[3,5],complet:[3,5],complic:6,config:6,connect:3,consid:5,constructor:3,consumer_result_label:5,contact:3,contain:[3,5],content:1,control:3,conveni:5,convert:3,convert_preprocessed_annot:3,could:[3,5],coupl:6,creat:[3,5,6],create_workflow:3,current:3,danger:3,data:[3,5,6],data_input:3,data_lin:[4,6],debug:3,decod:[3,5],definit:[3,5],desir:3,detail:3,dict:3,dictionari:[3,4,5,6],did:3,differ:6,disconnect:3,document:[3,5],doe:[3,5],domain:3,done:[3,5],due:5,e:[3,5],each:[3,5],easier:5,either:[3,5],element:[3,5],emb:3,encod:[3,5],end:[3,5],end_m:3,end_region:[4,6],end_region_m:3,end_second:3,enhanc:3,enhans:3,enqueue_request:3,enqueuerequest:3,enrol:[3,5],enroll_cli:[1,2],enterpris:[2,3],error:[3,5],error_mssag:3,etc:3,ever:3,exampl:[3,6],except:[3,5],exceptionfromserv:5,execut:3,exist:[3,5],exit:3,extract:5,fail:[3,5],fals:3,fetch:5,file:[3,4,5,6],file_annot:3,filenam:[3,4,5,6],filesystem:[3,5],filter:3,filter_length:[3,6],finalize_supervised_adapt:3,find:5,former:6,found:3,four:3,frame:3,framescorerresult:3,from:[3,4,5,6],full:3,fullobj:3,gener:[3,5],get:[3,5],get_act:3,get_analysis_class_id:3,get_analysis_job:5,get_analysis_job_nam:3,get_analysis_job_result:5,get_analysis_task:[3,5],get_analysis_task_info:3,get_analysis_task_result:5,get_bit_depth:3,get_enrollment_job_nam:3,get_enrollment_task:3,get_error:5,get_failed_task:5,get_fullobj:3,get_info:3,get_json:3,get_request_job:5,get_respons:5,get_response_as_json:5,get_statu:3,get_unenrollment_job_nam:3,get_unenrollment_task:3,get_update_statu:3,get_uuid:5,get_workflow:5,get_workflow_job:5,get_workflow_job_nam:5,get_workflow_job_task:5,get_workflow_typ:5,getactiverequest:3,getactiveresult:3,getstatusrequest:3,getstatusresult:3,getupdatestatusrequest:3,getupdatestatusresult:3,global:3,globalscorerresult:3,group:[3,4,6],ha:[3,4,5],handl:[3,5],have:[3,5],heartbeat:[3,4],heartbeat_callback:3,heartbeat_notif:4,heatbeat:4,help:3,helper:[3,5],how:3,howev:5,hte:3,i:[3,5],id:3,ident:5,ignor:3,imag:3,image_input:3,immedi:3,implement:2,includ:[3,5],indent:[3,5],index:[0,3,5],indic:3,info:[3,5],inform:3,input:[3,5,6],insert:[3,5],instanc:3,instead:3,integ:[3,5],intend:[3,5,6],interact:[3,4],interpol:[3,6],invok:3,is_allowable_error:5,is_connect:3,is_error:5,is_success:5,isn:5,issu:3,job:[3,5,6],job_nam:[3,5],joshua:3,json:[3,5,6],just:5,keyword:3,known:3,kwarg:3,label:3,labl:3,languag:3,last:3,latter:6,learn_client:[1,2],let:5,level:[3,5],librari:3,lid:3,like:[3,5,6],line:[3,6],list:[3,5,6],listen:3,load:3,load_plugin_domain:3,loadplugindomainrequest:3,loadplugindomainresult:3,local:[3,5],localhost:3,mai:[3,5,6],main:[3,4],make:[3,5],map:5,mean:[3,5],member:[3,5],memori:3,messag:[1,2,3,4],message_typ:5,metadata:3,method:[3,4,5],midg:[3,6],millisecond:3,mode:[3,5],modif:3,modifi:3,modul:[0,1],monitor:3,monitor_serv:3,monitor_statu:3,more:[3,6],most:[3,5],msgutil:[1,2],multi:[3,5],multilang:3,multipl:[3,5],multithread:3,must:[3,5],my_callback:3,name:[3,5,6],ndarrai:3,need:3,neg:[3,5],network:3,new_domain_nam:3,newlin:[3,5],non:[3,5],none:[3,5],normal:5,note:[3,5],notifi:[3,4],ns:3,num_channel:[3,5],num_sampl:[3,5],number:[3,5],numpi:3,object:[3,5,6],occur:[3,5],often:5,old:5,oliv:[2,3,4,5,6],olive_async_cli:[1,2],olive_pb2:[1,2],oliveclassstatusrespons:[3,5],olivecli:[1,2],oliveserv:3,oliveserverrespons:[3,5],oliveworkflow:3,oliveworkflowactualizedrespons:5,oliveworkflowanalysisrespons:[3,5],oliveworkflowdefinit:3,oliveworkflowenrollmentrespons:5,onc:3,one:[3,5,6],onli:[3,5,6],open:[3,5],open_config:6,opt:3,option:[3,5,6],option_str:6,optionvalu:6,order:5,origin:[3,5],otherwis:[3,5],outbound:3,output:[3,5],over:3,overrid:3,packag:1,package_audio:[3,5],package_buffer_audio:3,package_imag:3,package_text:3,package_video:3,page:0,pair:[3,6],param:[3,4,5],paramet:[3,5,6],pars:[3,4,5,6],parse_annotation_fil:3,parse_file_opt:6,parse_from_respons:5,parse_json_opt:6,parse_json_options_as_dict:6,parse_pem_fil:[4,6],pass:[3,6],path:[3,5],pathnam:5,pcm:[3,5],pcm_16:3,pem:[4,6],perform:3,phase:3,plain:5,plugin:[3,5],plugindirectoryrequest:3,plugindirectoryresult:3,popul:[3,5],possibl:[3,5],pre:3,preprocess:3,preprocess_supervised_audio:3,pretti:[3,5],print:[3,5],print_plugin:4,process:[3,5],processed_audio_list:3,program:3,properli:3,protobuf:5,provid:[3,5],py3:3,python:[2,3,5],qbe:3,queri:3,queue:3,rais:[3,5],rate:[3,5],raw:[3,5],read:[3,5],reason:5,receiv:[3,4],recent:3,region:[3,4,5,6],regionscorerresult:[3,5],regist:3,remov:3,removeplugindomainrequest:3,removeplugindomainresult:3,report:3,repres:3,represent:[3,5],request:[3,5],request_plugin:3,request_port:3,requir:3,requst:3,requst_sad_adapt:3,respect:3,respons:[1,2,3],result:[3,4,5,6],run:[3,5],runtim:3,s:[3,5],sad:[3,6],same:[3,5],sampl:[3,5],sample_r:[3,5],score:[3,4,5,6],search:0,second:[3,5],select:5,selected_channel:[3,5],send:[3,5],sent:[3,5],sequenti:3,serial:[3,5],serialize_audio:[3,5],server:[3,4,5],set:[3,5],setup_multithread:3,shl:3,should:[3,5],sid:3,side:5,signal:3,signatur:3,simpl:[3,6],simplifi:3,sinc:[3,5],so:[3,5],some:3,sort_global_scor:5,speaker:3,special:5,specifi:[3,5,6],speech:[3,5],standard:3,start:[3,5],start_m:3,start_region:[4,6],start_region_m:3,start_second:3,statu:3,status_cli:[1,2],status_socket:3,stopwork:3,str:[3,5],string:[3,5,6],structur:[3,5,6],subclass:3,submiss:5,submit:[3,5],submodul:[1,2],subpackag:1,success:3,support:[3,5,6],sure:3,sy:4,sync_request:3,synchron:3,t:5,taken:3,target:3,task:[3,5,6],task_annot:3,task_nam:5,tbd:3,test:3,test_nam:3,text:3,text_input:3,thei:[3,6],thi:[3,5],those:3,thread:3,three:5,threshold:3,thrown:5,time:3,timeout_second:3,to_json:[3,5],token:3,tradit:5,transfer:3,troubleshoot:3,tupl:[3,5],type:[3,5],typic:5,un:3,unabl:5,unenrol:[3,5],uniqu:3,unload:3,unload_plugin_domain:3,until:3,up:[3,5],updat:[3,5],update_plugin_domain:3,us:[3,5,6],usual:3,util:[1,2],utils_cli:[1,2],v1:3,v5:3,valid:[3,5],validate_local_path:[3,5],valu:[3,5,6],veri:3,versa:5,version:3,via:3,vice:5,video:3,video_input:3,wa:[3,5],wai:3,wait:3,want:[3,5],wav:3,we:[3,5],well:5,when:[3,5],whenev:5,where:[3,5],which:[3,5],whole:3,within:3,work:3,workflow:[1,2,4,5,6],workflow_adapt_typ:5,workflow_analysis_typ:5,workflow_cem:[1,2],workflow_cli:[1,2],workflow_definit:5,workflow_enroll_cli:[1,2],workflow_enrollment_typ:5,workflow_typ:5,workflowclassstatusresult:5,workflowdatarequest:3,workflowexcept:3,workflowtask:5,workflowtyp:5,wrap:[3,5],wrapper:[3,5],yet:3,you:3,your:3},titles:["Welcome to the OlivePy documentation!","olivepy","olivepy package","olivepy.api package","olivepy.client package","olivepy.messaging package","olivepy.utils package"],titleterms:{analyze_cli:4,api:3,client:4,content:[2,3,4,5,6],document:0,enroll_cli:4,indic:0,learn_client:4,messag:5,modul:[2,3,4,5,6],msgutil:5,olive_async_cli:3,olive_pb2:5,olivecli:3,olivepi:[0,1,2,3,4,5,6],packag:[2,3,4,5,6],respons:5,status_cli:4,submodul:[3,4,5,6],subpackag:2,tabl:0,util:6,utils_cli:4,welcom:0,workflow:3,workflow_cem:4,workflow_cli:4,workflow_enroll_cli:4}})